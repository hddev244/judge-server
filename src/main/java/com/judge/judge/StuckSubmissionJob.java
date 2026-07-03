package com.judge.judge;

import com.judge.queue.JudgeQueueService;
import com.judge.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fails submissions stuck in JUDGING with no live Redis lock — the worker that
 * owned them crashed. The @Version column already prevents a race with a worker
 * that is legitimately still processing (it would hold a lock anyway).
 */
@Component
public class StuckSubmissionJob {

    private static final Logger log = LoggerFactory.getLogger(StuckSubmissionJob.class);

    private final SubmissionRepository submissionRepository;
    private final JudgeQueueService queueService;
    private final SubmissionPersistenceService persistence;

    @Value("${judge.stuck-timeout-minutes:15}")
    private long stuckTimeoutMinutes;

    public StuckSubmissionJob(SubmissionRepository submissionRepository,
                              JudgeQueueService queueService,
                              SubmissionPersistenceService persistence) {
        this.submissionRepository = submissionRepository;
        this.queueService = queueService;
        this.persistence = persistence;
    }

    @Scheduled(fixedDelay = 60_000)
    public void reapStuck() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(stuckTimeoutMinutes);
        List<String> ids = submissionRepository.findStuckJudgingIds(threshold);
        for (String id : ids) {
            if (queueService.isLocked(id)) continue;   // still being worked on
            try {
                persistence.failSubmission(id, "SE", "Judging timed out (worker lost)");
                log.warn("Reaped stuck submission {}", id);
            } catch (Exception e) {
                log.error("Failed to reap stuck submission {}", id, e);
            }
        }
    }
}
