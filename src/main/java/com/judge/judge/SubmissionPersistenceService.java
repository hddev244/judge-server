package com.judge.judge;

import com.judge.domain.*;
import com.judge.repository.*;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * All DB writes for a judge run, each in its own short REQUIRES_NEW transaction.
 * This keeps the minutes-long docker loop (in {@link JudgeService}) out of any
 * transaction, so partial results are committed as they happen and a crash
 * mid-judge doesn't roll back everything.
 */
@Service
public class SubmissionPersistenceService {

    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionResultRepository submissionResultRepository;
    private final SubtaskRepository subtaskRepository;

    public SubmissionPersistenceService(SubmissionRepository submissionRepository,
                                        TestCaseRepository testCaseRepository,
                                        SubmissionResultRepository submissionResultRepository,
                                        SubtaskRepository subtaskRepository) {
        this.submissionRepository = submissionRepository;
        this.testCaseRepository = testCaseRepository;
        this.submissionResultRepository = submissionResultRepository;
        this.subtaskRepository = subtaskRepository;
    }

    /**
     * Marks the submission JUDGING (optimistic-lock guarded) and returns a
     * detached snapshot of everything the judge loop needs. Returns null if the
     * submission no longer exists.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JudgeJob startJudging(String submissionId) {
        Submission s = submissionRepository.findById(submissionId).orElse(null);
        if (s == null) return null;
        s.setStatus("JUDGING");
        submissionRepository.save(s);   // @Version throws if another worker raced us

        Problem p = s.getProblem();
        List<JudgeJob.TestCaseView> tcs = new ArrayList<>();
        for (TestCase tc : testCaseRepository.findByProblemIdOrderByOrderIndexAsc(p.getId())) {
            tcs.add(new JudgeJob.TestCaseView(
                    tc.getId(), tc.getInputPath(), tc.getOutputPath(),
                    tc.getScore(), tc.isSample(),
                    tc.getSubtask() != null ? tc.getSubtask().getId() : null));
        }
        List<JudgeJob.SubtaskView> sts = new ArrayList<>();
        for (Subtask st : subtaskRepository.findByProblemIdOrderByOrderIndexAsc(p.getId())) {
            sts.add(new JudgeJob.SubtaskView(st.getId(), st.getName(), st.getScore()));
        }

        return new JudgeJob(
                s.getId(), s.getLanguage(), s.getSourceCode(),
                p.getId(), p.getTimeLimitMs(), p.getMemoryLimitKb(),
                p.getCheckerType(), p.getCheckerLanguage(), p.getCheckerBinPath(),
                "ALL",
                tcs, sts);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveResult(String submissionId, Long testCaseId,
                           String verdict, int timeMs, int memoryKb) {
        submissionResultRepository.save(SubmissionResult.builder()
                .submission(submissionRepository.getReferenceById(submissionId))
                .testCase(testCaseId != null ? testCaseRepository.getReferenceById(testCaseId) : null)
                .status(verdict)
                .timeMs(timeMs)
                .memoryKb(memoryKb)
                .build());
    }

    /** Sets the final verdict and returns the submission with problem+results initialized. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Submission finalizeSubmission(String submissionId, String status,
                                         int score, int timeMs) {
        Submission s = submissionRepository.findById(submissionId).orElseThrow();
        s.setStatus(status);
        s.setScore(score);
        s.setTimeMs(timeMs);
        s.setFinishedAt(LocalDateTime.now());
        submissionRepository.save(s);
        Hibernate.initialize(s.getProblem());
        Hibernate.initialize(s.getResults());
        return s;
    }

    /** Terminal failure (CE/SE) with a message; returns the initialized submission. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Submission failSubmission(String submissionId, String status, String message) {
        Submission s = submissionRepository.findById(submissionId).orElseThrow();
        s.setStatus(status);
        s.setErrorMessage(message);
        s.setFinishedAt(LocalDateTime.now());
        submissionRepository.save(s);
        Hibernate.initialize(s.getProblem());
        Hibernate.initialize(s.getResults());
        return s;
    }
}
