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
import com.judge.webhook.WebhookSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public JudgeService(SubmissionRepository submissionRepository,
                        TestCaseRepository testCaseRepository,
                        SubmissionResultRepository submissionResultRepository,
                        SubtaskRepository subtaskRepository,
                        ProblemRepository problemRepository,
                        DockerRunner dockerRunner,
                        OutputComparator comparator,
                        JudgeQueueService queueService,
                        WebhookSender webhookSender,
                        JudgeConfig judgeConfig) {
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
    }

    @Transactional
    public void judge(String submissionId) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            log.warn("Submission not found: {}", submissionId);
            return;
        }

        // Validate language
        if (!judgeConfig.getLanguages().containsKey(submission.getLanguage())) {
            submission.setStatus("CE");
            submission.setErrorMessage("Unsupported language: " + submission.getLanguage());
            submission.setFinishedAt(LocalDateTime.now());
            submissionRepository.save(submission);
            return;
        }

        submission.setStatus("JUDGING");
        submissionRepository.save(submission);

        Problem problem = submission.getProblem();
        List<TestCase> testCases = testCaseRepository
                .findByProblemIdOrderByOrderIndexAsc(problem.getId());
        List<Subtask> subtasks = subtaskRepository
                .findByProblemIdOrderByOrderIndexAsc(problem.getId());

        String finalVerdict = "AC";
        int totalScore = 0;
        long maxTimeMs = 0;

        try {
            CompileResult compileResult = dockerRunner.compile(
                    submission.getLanguage(),
                    submission.getSourceCode(),
                    submissionId
            );

            if (!compileResult.isSuccess()) {
                submission.setStatus("CE");
                submission.setErrorMessage(compileResult.getErrorOutput());
                submission.setFinishedAt(LocalDateTime.now());
                submissionRepository.save(submission);
                return;
            }

            // Track per-subtask pass/fail and per-case verdicts
            Map<Long, Boolean> subtaskPassed = new HashMap<>();
            for (Subtask st : subtasks) subtaskPassed.put(st.getId(), true);
            Map<Long, String> tcVerdicts = new LinkedHashMap<>();

            for (TestCase tc : testCases) {
                RunResult rr = dockerRunner.run(
                        compileResult.getWorkDir(),
                        submission.getLanguage(),
                        tc.getInputPath(),
                        problem.getTimeLimitMs(),
                        problem.getMemoryLimitKb()
                );

                String verdict = evaluate(rr, tc, problem, compileResult.getWorkDir());
                tcVerdicts.put(tc.getId(), verdict);

                submissionResultRepository.save(SubmissionResult.builder()
                        .submission(submission)
                        .testCase(tc)
                        .status(verdict)
                        .timeMs((int) rr.getTimeMs())
                        .memoryKb((int) rr.getMemoryKb())
                        .build());

                if (!"AC".equals(verdict)) {
                    if ("AC".equals(finalVerdict)) finalVerdict = verdict;
                    if (tc.getSubtask() != null) {
                        subtaskPassed.put(tc.getSubtask().getId(), false);
                    }
                }

                maxTimeMs = Math.max(maxTimeMs, rr.getTimeMs());
            }

            // Score: subtask-based or per-case
            if (!subtasks.isEmpty()) {
                for (Subtask st : subtasks) {
                    if (subtaskPassed.getOrDefault(st.getId(), false)) {
                        totalScore += st.getScore();
                    }
                }
                for (TestCase tc : testCases) {
                    if (tc.getSubtask() == null && "AC".equals(tcVerdicts.get(tc.getId()))) {
                        totalScore += tc.getScore();
                    }
                }
            } else {
                for (TestCase tc : testCases) {
                    if ("AC".equals(tcVerdicts.get(tc.getId()))) totalScore += tc.getScore();
                }
            }

            submission.setStatus(testCases.isEmpty() ? "AC" : finalVerdict);
            submission.setScore(totalScore);
            submission.setTimeMs((int) maxTimeMs);
            submission.setFinishedAt(LocalDateTime.now());
            submissionRepository.save(submission);
            webhookSender.sendAsync(submission);

        } catch (IOException e) {
            log.error("Judge error for submission {}", submissionId, e);
            submission.setStatus("SE");
            submission.setErrorMessage("Internal judge error: " + e.getMessage());
            submission.setFinishedAt(LocalDateTime.now());
            submissionRepository.save(submission);
        } finally {
            dockerRunner.cleanup(submissionId);
            queueService.markDone(submissionId);
        }
    }

    public SubmissionResponse runTest(TestRunRequest req) {
        if (!judgeConfig.getLanguages().containsKey(req.getLanguage())) {
            throw JudgeException.badRequest("Unsupported language: " + req.getLanguage());
        }

        Problem problem = problemRepository.findById(req.getProblemId())
                .filter(Problem::isPublished)
                .orElseThrow(() -> JudgeException.notFound("Problem not found or not published"));

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
                String verdict = evaluate(rr, tc, problem, cr.getWorkDir());
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

    private String evaluate(RunResult rr, TestCase tc, Problem problem, String workDir) {
        if (rr.isTimedOut())       return "TLE";
        if (rr.isMemoryExceeded()) return "MLE";
        if (rr.getExitCode() != 0) return "RE";

        if ("CUSTOM".equals(problem.getCheckerType()) && problem.getCheckerBinPath() != null) {
            try {
                return dockerRunner.runChecker(
                        problem.getCheckerBinPath(),
                        tc.getInputPath(),
                        tc.getOutputPath(),
                        rr.getStdout(),
                        workDir
                );
            } catch (IOException e) {
                log.error("Checker error for tc={}", tc.getId(), e);
                return "SE";
            }
        }

        return comparator.compare(rr.getStdout(), tc.getOutputPath()) ? "AC" : "WA";
    }
}
