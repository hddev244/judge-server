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
    private final ScoringService scoringService;

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
                        SubmissionPersistenceService persistence,
                        ScoringService scoringService) {
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
        this.scoringService = scoringService;
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

            // Ratio (0..1) awarded per test-case id; ScoringService aggregates it.
            Map<Long, Double> caseRatios = new HashMap<>();
            Set<Long> failedSubtasks = new HashSet<>();

            String finalVerdict = "AC";
            long maxTimeMs = 0;
            String mode = job.judgingMode() != null ? job.judgingMode() : "ALL";

            for (JudgeJob.TestCaseView tc : job.testCases()) {
                // Early-exit: skip remaining cases of an already-failed subtask, or all
                // remaining cases once any case failed (STOP_ON_FIRST_FAIL).
                boolean skip = ("SUBTASK_SKIP".equals(mode) && tc.subtaskId() != null
                                && failedSubtasks.contains(tc.subtaskId()))
                            || ("STOP_ON_FIRST_FAIL".equals(mode) && !"AC".equals(finalVerdict));
                if (skip) {
                    persistence.saveResult(submissionId, tc.id(), "SKIPPED", 0, 0, 0.0);
                    caseRatios.put(tc.id(), 0.0);
                    continue;
                }

                CaseOutcome oc;
                int caseTimeMs, caseMemKb;
                if ("INTERACTIVE".equals(job.checkerType()) && job.checkerBinPath() != null) {
                    var ir = dockerRunner.runInteractive(
                            compileResult.getWorkDir(), job.language(),
                            job.checkerBinPath(), job.checkerLanguage(),
                            tc.inputPath(), tc.outputPath(), job.timeLimitMs(), job.memoryLimitKb());
                    oc = new CaseOutcome(ir.verdict(), ir.ratio());
                    caseTimeMs = (int) ir.timeMs();
                    caseMemKb = (int) ir.memoryKb();
                } else {
                    RunResult rr = dockerRunner.run(
                            compileResult.getWorkDir(), job.language(),
                            tc.inputPath(), job.timeLimitMs(), job.memoryLimitKb());
                    oc = evaluate(rr, tc.inputPath(), tc.outputPath(), job, compileResult.getWorkDir());
                    caseTimeMs = (int) rr.getTimeMs();
                    caseMemKb = (int) rr.getMemoryKb();
                }

                persistence.saveResult(submissionId, tc.id(), oc.verdict(),
                        caseTimeMs, caseMemKb, oc.ratio());

                partialResults.add(JudgeStatusPublisher.TestCaseUpdate.builder()
                        .testCaseId(tc.id()).status(oc.verdict())
                        .timeMs(caseTimeMs).memoryKb(caseMemKb)
                        .build());
                statusPublisher.publishPartial(submissionId, partialResults);

                caseRatios.put(tc.id(), oc.ratio());
                if (!"AC".equals(oc.verdict())) {
                    if ("AC".equals(finalVerdict)) finalVerdict = oc.verdict();
                    if (tc.subtaskId() != null) failedSubtasks.add(tc.subtaskId());
                }
                maxTimeMs = Math.max(maxTimeMs, caseTimeMs);
            }

            int totalScore = scoringService.total(job.subtasks(), job.testCases(), caseRatios);

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
                CaseOutcome oc;
                int caseTimeMs, caseMemKb;
                if ("INTERACTIVE".equals(problem.getCheckerType()) && problem.getCheckerBinPath() != null) {
                    var ir = dockerRunner.runInteractive(
                            cr.getWorkDir(), req.getLanguage(),
                            problem.getCheckerBinPath(), problem.getCheckerLanguage(),
                            tc.getInputPath(), tc.getOutputPath(),
                            problem.getTimeLimitMs(), problem.getMemoryLimitKb());
                    oc = new CaseOutcome(ir.verdict(), ir.ratio());
                    caseTimeMs = (int) ir.timeMs();
                    caseMemKb = (int) ir.memoryKb();
                } else {
                    RunResult rr = dockerRunner.run(
                            cr.getWorkDir(), req.getLanguage(),
                            tc.getInputPath(), problem.getTimeLimitMs(), problem.getMemoryLimitKb());
                    oc = evaluate(rr, tc.getInputPath(), tc.getOutputPath(),
                            problem.getCheckerType(), problem.getCheckerLanguage(), problem.getCheckerBinPath(),
                            problem.getComparisonMode(), problem.getFloatEpsilon(), cr.getWorkDir());
                    caseTimeMs = (int) rr.getTimeMs();
                    caseMemKb = (int) rr.getMemoryKb();
                }
                results.add(SubmissionResponse.TestResultDto.builder()
                        .testCaseId(tc.getId()).status(oc.verdict())
                        .timeMs(caseTimeMs).memoryKb(caseMemKb)
                        .build());
                totalScore += (int) Math.round(tc.getScore() * oc.ratio());
                if (!"AC".equals(oc.verdict()) && "AC".equals(finalVerdict)) finalVerdict = oc.verdict();
                maxTimeMs = Math.max(maxTimeMs, caseTimeMs);
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

    /** Per-test-case verdict plus the fraction of the case score awarded (0..1). */
    public record CaseOutcome(String verdict, double ratio) {
        static CaseOutcome ac()          { return new CaseOutcome("AC", 1.0); }
        static CaseOutcome of(String v)  { return new CaseOutcome(v, "AC".equals(v) ? 1.0 : 0.0); }
    }

    private CaseOutcome evaluate(RunResult rr, String inputPath, String expectedPath,
                                 JudgeJob job, String workDir) {
        return evaluate(rr, inputPath, expectedPath, job.checkerType(), job.checkerLanguage(),
                job.checkerBinPath(), job.comparisonMode(), job.floatEpsilon(), workDir);
    }

    private CaseOutcome evaluate(RunResult rr, String inputPath, String expectedPath,
                                 String checkerType, String checkerLanguage, String checkerBinPath,
                                 String comparisonMode, Double floatEpsilon, String workDir) {
        if (rr.isSystemError())    return CaseOutcome.of("SE");
        if (rr.isTimedOut())       return CaseOutcome.of("TLE");
        if (rr.isMemoryExceeded()) return CaseOutcome.of("MLE");
        if (rr.getExitCode() != 0) return CaseOutcome.of("RE");

        if ("CUSTOM".equals(checkerType) && checkerBinPath != null) {
            try {
                var cr = dockerRunner.runChecker(checkerBinPath, checkerLanguage,
                        inputPath, expectedPath, rr.getStdout(), workDir);
                return new CaseOutcome(cr.verdict(), cr.ratio());
            } catch (IOException e) {
                log.error("Checker error for input={}", inputPath, e);
                return CaseOutcome.of("SE");
            }
        }

        boolean ok = comparator.compare(rr.getStdout(), expectedPath, comparisonMode, floatEpsilon);
        return ok ? CaseOutcome.ac() : CaseOutcome.of("WA");
    }
}
