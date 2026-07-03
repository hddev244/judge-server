package com.judge.judge;

import com.judge.api.dto.SubmissionResponse;
import com.judge.api.dto.TestRunRequest;
import com.judge.config.JudgeConfig;
import com.judge.domain.*;
import com.judge.exception.JudgeException;
import com.judge.judge.model.CompileResult;
import com.judge.judge.model.RunResult;
import com.judge.queue.JudgeQueueService;
import com.judge.repository.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import com.judge.webhook.WebhookSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionResultRepository submissionResultRepository;
    private final SubtaskRepository subtaskRepository;
    private final ProblemRepository problemRepository;
    private final DockerRunner dockerRunner;
    private final OutputComparator comparator;
    private final JudgeQueueService queueService;
    private final WebhookSender webhookSender;
    private final JudgeConfig judgeConfig;
    private final JudgeStatusPublisher statusPublisher;
    private final CacheManager cacheManager;
    private final SubmissionPersistenceService persistence;

    public JudgeService(SubmissionRepository submissionRepository,
                        TestCaseRepository testCaseRepository,
                        SubmissionResultRepository submissionResultRepository,
                        SubtaskRepository subtaskRepository,
                        ProblemRepository problemRepository,
                        DockerRunner dockerRunner,
                        OutputComparator comparator,
                        JudgeQueueService queueService,
                        WebhookSender webhookSender,
                        JudgeConfig judgeConfig,
                        JudgeStatusPublisher statusPublisher,
                        CacheManager cacheManager,
                        SubmissionPersistenceService persistence) {
        this.submissionRepository = submissionRepository;
        this.testCaseRepository = testCaseRepository;
        this.submissionResultRepository = submissionResultRepository;
        this.subtaskRepository = subtaskRepository;
        this.problemRepository = problemRepository;
        this.dockerRunner = dockerRunner;
        this.comparator = comparator;
        this.queueService = queueService;
        this.webhookSender = webhookSender;
        this.judgeConfig = judgeConfig;
        this.statusPublisher = statusPublisher;
        this.cacheManager = cacheManager;
        this.persistence = persistence;
    }

    private void evictLeaderboardCache() {
        var cache = cacheManager.getCache("leaderboard");
        if (cache != null) cache.clear();
    }

    /**
     * Orchestrates a full judge run. NOT transactional: the minutes-long docker
     * loop holds no DB connection. Each DB write goes through {@link #persistence}
     * in its own short transaction, so partial results survive a crash.
     */
    public void judge(String submissionId) {
        JudgeJob job;
        try {
            job = persistence.startJudging(submissionId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Submission {} already being processed (version conflict), skipping", submissionId);
            return;
        }
        if (job == null) {
            log.warn("Submission not found: {}", submissionId);
            return;
        }

        if (!judgeConfig.getLanguages().containsKey(job.language())) {
            Submission s = persistence.failSubmission(submissionId, "CE",
                    "Unsupported language: " + job.language());
            statusPublisher.publishFinal(s, List.of());
            return;
        }

        List<JudgeStatusPublisher.TestCaseUpdate> partialResults = new ArrayList<>();
        try {
            CompileResult compileResult = dockerRunner.compile(
                    job.language(), job.sourceCode(), submissionId);

            if (compileResult.isSystemError()) {
                log.error("Docker unavailable during compile for submission {}", submissionId);
                Submission s = persistence.failSubmission(submissionId, "SE",
                        "Docker daemon unavailable: " + compileResult.getErrorOutput());
                statusPublisher.publishFinal(s, List.of());
                return;
            }
            if (!compileResult.isSuccess()) {
                Submission s = persistence.failSubmission(submissionId, "CE",
                        compileResult.getErrorOutput());
                statusPublisher.publishFinal(s, List.of());
                return;
            }

            Map<Long, Boolean> subtaskPassed = new HashMap<>();
            for (JudgeJob.SubtaskView st : job.subtasks()) subtaskPassed.put(st.id(), true);
            Map<Long, String> tcVerdicts = new LinkedHashMap<>();

            String finalVerdict = "AC";
            long maxTimeMs = 0;

            for (JudgeJob.TestCaseView tc : job.testCases()) {
                RunResult rr = dockerRunner.run(
                        compileResult.getWorkDir(), job.language(),
                        tc.inputPath(), job.timeLimitMs(), job.memoryLimitKb());

                String verdict = evaluate(rr, tc.inputPath(), tc.outputPath(),
                        job.checkerType(), job.checkerBinPath(), compileResult.getWorkDir());
                tcVerdicts.put(tc.id(), verdict);

                persistence.saveResult(submissionId, tc.id(), verdict,
                        (int) rr.getTimeMs(), (int) rr.getMemoryKb());

                partialResults.add(JudgeStatusPublisher.TestCaseUpdate.builder()
                        .testCaseId(tc.id()).status(verdict)
                        .timeMs((int) rr.getTimeMs()).memoryKb((int) rr.getMemoryKb())
                        .build());
                statusPublisher.publishPartial(submissionId, partialResults);

                if (!"AC".equals(verdict)) {
                    if ("AC".equals(finalVerdict)) finalVerdict = verdict;
                    if (tc.subtaskId() != null) subtaskPassed.put(tc.subtaskId(), false);
                }
                maxTimeMs = Math.max(maxTimeMs, rr.getTimeMs());
            }

            int totalScore = 0;
            if (!job.subtasks().isEmpty()) {
                for (JudgeJob.SubtaskView st : job.subtasks()) {
                    if (subtaskPassed.getOrDefault(st.id(), false)) totalScore += st.score();
                }
                for (JudgeJob.TestCaseView tc : job.testCases()) {
                    if (tc.subtaskId() == null && "AC".equals(tcVerdicts.get(tc.id())))
                        totalScore += tc.score();
                }
            } else {
                for (JudgeJob.TestCaseView tc : job.testCases()) {
                    if ("AC".equals(tcVerdicts.get(tc.id()))) totalScore += tc.score();
                }
            }

            String status = job.testCases().isEmpty() ? "AC" : finalVerdict;
            Submission s = persistence.finalizeSubmission(submissionId, status, totalScore, (int) maxTimeMs);
            statusPublisher.publishFinal(s, partialResults);
            webhookSender.sendAsync(s);
            if (s.getUserRef() != null) evictLeaderboardCache();

        } catch (IOException e) {
            log.error("Judge error for submission {}", submissionId, e);
            Submission s = persistence.failSubmission(submissionId, "SE",
                    "Internal judge error: " + e.getMessage());
            statusPublisher.publishFinal(s, List.of());
        } finally {
            dockerRunner.cleanup(submissionId);
        }
    }

    public SubmissionResponse runTest(TestRunRequest req) {
        if (!judgeConfig.getLanguages().containsKey(req.getLanguage())) {
            throw JudgeException.badRequest("Unsupported language: " + req.getLanguage());
        }

        Problem problem = problemRepository.findById(req.getProblemId())
                .filter(p -> !"PRIVATE".equals(p.getStatus()))
                .orElseThrow(() -> JudgeException.notFound("Problem not found or not available"));

        List<TestCase> samples = testCaseRepository
                .findByProblemIdOrderByOrderIndexAsc(problem.getId())
                .stream().filter(TestCase::isSample).toList();

        if (samples.isEmpty()) {
            return SubmissionResponse.builder()
                    .status("SE").score(0).testRun(true)
                    .errorMessage("Bài này chưa có test case mẫu (sample).")
                    .language(req.getLanguage()).testResults(List.of()).build();
        }

        String jobId = "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        try {
            CompileResult cr = dockerRunner.compile(req.getLanguage(), req.getSourceCode(), jobId);
            if (!cr.isSuccess()) {
                return SubmissionResponse.builder()
                        .status("CE").score(0).testRun(true)
                        .errorMessage(cr.getErrorOutput())
                        .language(req.getLanguage()).testResults(List.of()).build();
            }

            List<SubmissionResponse.TestResultDto> results = new ArrayList<>();
            String finalVerdict = "AC";
            int totalScore = 0;
            long maxTimeMs = 0;

            for (TestCase tc : samples) {
                RunResult rr = dockerRunner.run(
                        cr.getWorkDir(), req.getLanguage(),
                        tc.getInputPath(), problem.getTimeLimitMs(), problem.getMemoryLimitKb());
                String verdict = evaluate(rr, tc.getInputPath(), tc.getOutputPath(),
                        problem.getCheckerType(), problem.getCheckerBinPath(), cr.getWorkDir());
                results.add(SubmissionResponse.TestResultDto.builder()
                        .testCaseId(tc.getId()).status(verdict)
                        .timeMs((int) rr.getTimeMs()).memoryKb((int) rr.getMemoryKb())
                        .build());
                if ("AC".equals(verdict)) totalScore += tc.getScore();
                else if ("AC".equals(finalVerdict)) finalVerdict = verdict;
                maxTimeMs = Math.max(maxTimeMs, rr.getTimeMs());
            }

            return SubmissionResponse.builder()
                    .status(finalVerdict).score(totalScore).timeMs((int) maxTimeMs)
                    .testRun(true).language(req.getLanguage()).testResults(results).build();

        } catch (IOException e) {
            log.error("Test run error", e);
            return SubmissionResponse.builder()
                    .status("SE").score(0).testRun(true)
                    .errorMessage("Lỗi hệ thống: " + e.getMessage())
                    .language(req.getLanguage()).testResults(List.of()).build();
        } finally {
            dockerRunner.cleanup(jobId);
        }
    }

    private String evaluate(RunResult rr, String inputPath, String expectedPath,
                            String checkerType, String checkerBinPath, String workDir) {
        if (rr.isSystemError())    return "SE";
        if (rr.isTimedOut())       return "TLE";
        if (rr.isMemoryExceeded()) return "MLE";
        if (rr.getExitCode() != 0) return "RE";

        if ("CUSTOM".equals(checkerType) && checkerBinPath != null) {
            try {
                return dockerRunner.runChecker(
                        checkerBinPath, inputPath, expectedPath, rr.getStdout(), workDir);
            } catch (IOException e) {
                log.error("Checker error for input={}", inputPath, e);
                return "SE";
            }
        }

        return comparator.compare(rr.getStdout(), expectedPath) ? "AC" : "WA";
    }
}
