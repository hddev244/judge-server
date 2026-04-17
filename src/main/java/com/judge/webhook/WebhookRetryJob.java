package com.judge.webhook;

import com.judge.domain.WebhookRetry;
import com.judge.repository.WebhookRetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class WebhookRetryJob {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryJob.class);

    private final WebhookRetryRepository retryRepository;
    private final WebhookSender webhookSender;

    public WebhookRetryJob(WebhookRetryRepository retryRepository, WebhookSender webhookSender) {
        this.retryRepository = retryRepository;
        this.webhookSender = webhookSender;
    }

    @Scheduled(fixedDelay = 60_000)
    public void retryFailedWebhooks() {
        List<WebhookRetry> pending = retryRepository.findPendingRetries(LocalDateTime.now());
        if (pending.isEmpty()) return;

        log.info("Retrying {} webhooks", pending.size());
        for (WebhookRetry retry : pending) {
            try {
                webhookSender.send(retry.getCallbackUrl(), retry.getPayload(),
                        retry.getSignature(), retry.getSubmissionId());
                retryRepository.delete(retry);
            } catch (Exception e) {
                retry.setAttemptCount(retry.getAttemptCount() + 1);
                long backoffSeconds = 30L * (1L << retry.getAttemptCount());
                retry.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
                if (retry.getAttemptCount() >= 4) {
                    retry.setFailedPermanently(true);
                    log.warn("Webhook permanently failed for submission {}", retry.getSubmissionId());
                }
                retryRepository.save(retry);
            }
        }
    }
}
