package com.judge.judge;

import com.judge.api.dto.SubmissionResponse;
import com.judge.api.dto.TestRunRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionResultRepository submissionResultRepository;
    private final ProblemRepository problemRepository;
    private final DockerRunner dockerRunner;
    private final OutputComparator comparator;
    private final JudgeQueueService queueService;
    private final WebhookSender webhookSender;

    public JudgeService(SubmissionRepository submissionRepository,
                        TestCaseRepository testCaseRepository,
                        SubmissionResultRepository submissionResultRepository,
                        ProblemRepository problemRepository,
                        DockerRunner dockerRunner,
                        OutputComparator comparator,
                        JudgeQueueService queueService,
                        WebhookSender webhookSender) {
        this.submissionRepository = submissionRepository;
        this.testCaseRepository = testCaseRepository;
        this.submissionResultRepository = submissionResultRepository;
        this.problemRepository = problemRepository;
        this.dockerRunner = dockerRunner;
        this.comparator = comparator;
        this.queueService = queueService;
        this.webhookSender = webhookSender;
    }

    @Transactional
    public void judge(String submissionId) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            log.warn("Submission not found: {}", submissionId);
            return;
        }

        submission.setStatus("JUDGING");
        submissionRepository.save(submission);

        List<TestCase> testCases = testCaseRepository
                .findByProblemIdOrderByOrderIndexAsc(submission.getProblem().getId());

        Problem problem = submission.getProblem();
        String finalVerdict = "AC";
        int totalScore = 0;
        long maxTimeMs = 0;
        CompileResult compileResult = null;

        try {
            compileResult = dockerRunner.compile(
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

            for (TestCase tc : testCases) {
                RunResult rr = dockerRunner.run(
                        compileResult.getWorkDir(),
                        submission.getLanguage(),
                        tc.getInputPath(),
                        problem.getTimeLimitMs(),
                        problem.getMemoryLimitKb()
                );

                String verdict = evaluate(rr, tc.getOutputPath());

                submissionResultRepository.save(SubmissionResult.builder()
                        .submission(submission)
                        .testCase(tc)
                        .status(verdict)
                        .timeMs((int) rr.getTimeMs())
                        .memoryKb((int) rr.getMemoryKb())
                        .build());

                if ("AC".equals(verdict)) {
                    totalScore += tc.getScore();
                } else if ("AC".equals(finalVerdict)) {
                    finalVerdict = verdict;
                }

                maxTimeMs = Math.max(maxTimeMs, rr.getTimeMs());
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
        Problem problem = problemRepository.findById(req.getProblemId())
                .filter(Problem::isPublished)
                .orElseThrow(() -> JudgeException.notFound("Problem not found or not published"));

        List<TestCase> samples = testCaseRepository
                .findByProblemIdOrderByOrderIndexAsc(problem.getId())
                .stream().filter(TestCase::isSample).toList();

        if (samples.isEmpty()) {
            return SubmissionResponse.builder()
                    .status("SE")
                    .score(0)
                    .testRun(true)
                    .errorMessage("Bài này chưa có test case mẫu (sample).")
                    .language(req.getLanguage())
                    .testResults(List.of())
                    .build();
        }

        String jobId = "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        try {
            CompileResult cr = dockerRunner.compile(req.getLanguage(), req.getSourceCode(), jobId);
            if (!cr.isSuccess()) {
                return SubmissionResponse.builder()
                        .status("CE")
                        .score(0)
                        .testRun(true)
                        .errorMessage(cr.getErrorOutput())
                        .language(req.getLanguage())
                        .testResults(List.of())
                        .build();
            }

            List<SubmissionResponse.TestResultDto> results = new ArrayList<>();
            String finalVerdict = "AC";
            int totalScore = 0;
            long maxTimeMs = 0;

            for (TestCase tc : samples) {
                RunResult rr = dockerRunner.run(
                        cr.getWorkDir(), req.getLanguage(),
                        tc.getInputPath(), problem.getTimeLimitMs(), problem.getMemoryLimitKb());
                String verdict = evaluate(rr, tc.getOutputPath());
                results.add(SubmissionResponse.TestResultDto.builder()
                        .testCaseId(tc.getId())
                        .status(verdict)
                        .timeMs((int) rr.getTimeMs())
                        .memoryKb((int) rr.getMemoryKb())
                        .build());
                if ("AC".equals(verdict)) totalScore += tc.getScore();
                else if ("AC".equals(finalVerdict)) finalVerdict = verdict;
                maxTimeMs = Math.max(maxTimeMs, rr.getTimeMs());
            }

            return SubmissionResponse.builder()
                    .status(finalVerdict)
                    .score(totalScore)
                    .timeMs((int) maxTimeMs)
                    .testRun(true)
                    .language(req.getLanguage())
                    .testResults(results)
                    .build();

        } catch (IOException e) {
            log.error("Test run error", e);
            return SubmissionResponse.builder()
                    .status("SE")
                    .score(0)
                    .testRun(true)
                    .errorMessage("Lỗi hệ thống: " + e.getMessage())
                    .language(req.getLanguage())
                    .testResults(List.of())
                    .build();
        } finally {
            dockerRunner.cleanup(jobId);
        }
    }

    private String evaluate(RunResult rr, String expectedPath) {
        if (rr.isTimedOut())       return "TLE";
        if (rr.isMemoryExceeded()) return "MLE";
        if (rr.getExitCode() != 0) return "RE";
        if (comparator.compare(rr.getStdout(), expectedPath)) return "AC";
        return "WA";
    }
}
