package com.judge.judge;

import com.judge.queue.JudgeQueueService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class JudgeWorker {

    private static final Logger log = LoggerFactory.getLogger(JudgeWorker.class);

    private final JudgeQueueService queueService;
    private final JudgeService judgeService;
    // Shared daemon that renews all in-flight locks so a long judge run never
    // loses its lock to TTL expiry.
    private final ScheduledExecutorService renewer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "judge-lock-renewer");
                t.setDaemon(true);
                return t;
            });

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
                processOne();
            } catch (Throwable t) {
                // Never let the loop die: an escaped error would silently retire
                // this worker slot until the whole app restarts.
                log.error("Worker loop error, backing off", t);
                sleep(2000);
            }
        }
    }

    private void processOne() {
        Optional<String> maybeId = queueService.dequeue();
        if (maybeId.isEmpty()) return;
        String id = maybeId.get();

        Optional<String> token = queueService.tryLock(id);
        if (token.isEmpty()) {
            log.warn("Submission {} already locked by another worker, skipping", id);
            return;
        }

        ScheduledFuture<?> renewal = renewer.scheduleAtFixedRate(
                () -> queueService.renewLock(id, token.get()), 20, 20, TimeUnit.SECONDS);
        try {
            log.info("Processing submission: {}", id);
            judgeService.judge(id);
        } catch (Throwable t) {
            log.error("Judging failed for {}", id, t);
        } finally {
            renewal.cancel(false);
            queueService.releaseLock(id, token.get());
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @PreDestroy
    void shutdown() {
        renewer.shutdownNow();
    }
}
