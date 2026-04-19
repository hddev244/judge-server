package com.judge.judge;

import com.judge.queue.JudgeQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class JudgeWorker {

    private static final Logger log = LoggerFactory.getLogger(JudgeWorker.class);

    private final JudgeQueueService queueService;
    private final JudgeService judgeService;

    public JudgeWorker(JudgeQueueService queueService, JudgeService judgeService) {
        this.queueService = queueService;
        this.judgeService = judgeService;
    }

    @Async("judgeExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void startWorker() {
        log.info("Judge worker started on thread: {}", Thread.currentThread().getName());
        while (!Thread.currentThread().isInterrupted()) {
            try {
                queueService.dequeue().ifPresent(id -> {
                    if (!queueService.tryLock(id)) {
                        log.warn("Submission {} already being processed by another worker, skipping", id);
                        return;
                    }
                    try {
                        log.info("Processing submission: {}", id);
                        judgeService.judge(id);
                    } finally {
                        queueService.releaseLock(id);
                    }
                });
            } catch (Exception e) {
                log.error("Worker error", e);
            }
        }
    }
}
