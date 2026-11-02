package com.judge.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.judge.api.dto.SubmissionResponse;
import com.judge.domain.Submission;
import com.judge.domain.WebhookRetry;
import com.judge.repository.WebhookRetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class WebhookSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);
    private static final String HMAC_ALGO = "HmacSHA256";

    private final WebhookRetryRepository retryRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String webhookSecret;

    public WebhookSender(WebhookRetryRepository retryRepository,
                         ObjectMapper objectMapper,
                         @Value("${JUDGE_WEBHOOK_SECRET:${judge.webhook-secret:}}") String webhookSecret) {
        this.retryRepository = retryRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.webhookSecret = webhookSecret;
    }

    public boolean hasDedicatedSecret() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    @Async
    public void sendAsync(Submission submission) {
        if (submission.getCallbackUrl() == null) return;
        try {
            SubmissionResponse payload = SubmissionResponse.from(submission);
            String json = objectMapper.writeValueAsString(payload);
            send(submission.getCallbackUrl(), json, signatureFor(json, submission.getCallbackUrl()),
                    submission.getId());
        } catch (Exception e) {
            log.error("Failed to send webhook for {}", submission.getId(), e);
        }
    }

    /**
     * Signature with the key currently configured. Legacy protocol keys the HMAC with the callback
     * URL, which every caller sees; a dedicated secret is the only signature an upstream can trust.
     */
    public String signatureFor(String json, String callbackUrl) {
        return sign(json, hasDedicatedSecret() ? webhookSecret : callbackUrl);
    }

    /** First delivery: a failure is queued in webhook_retries for {@link WebhookRetryJob}. */
    public void send(String url, String json, String signature, String submissionId) {
        try {
            deliver(url, json, signature, submissionId);
        } catch (Exception e) {
            log.warn("Webhook failed for {}, scheduling retry: {}", submissionId, e.getMessage());
            retryRepository.save(WebhookRetry.builder()
                    .submissionId(submissionId)
                    .callbackUrl(url)
                    .payload(json)
                    .signature(signature)
                    .attemptCount(1)
                    .nextRetryAt(LocalDateTime.now().plusSeconds(30))
                    .failedPermanently(false)
                    .build());
        }
    }

    /**
     * One POST that throws on failure. The retry job must use this rather than {@link #send}: send
     * swallows the error and queues a fresh row, so the job used to delete the old row as
     * "delivered" and the new one restarted at attempt 1 — an undeliverable webhook was retried
     * every minute forever and never reached the permanent-failure cap.
     */
    public void deliver(String url, String json, String signature, String submissionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Judge-Signature", signature);
        headers.set("X-Judge-Submission-Id", submissionId);
        restTemplate.postForEntity(url, new HttpEntity<>(json, headers), Void.class);
        log.info("Webhook delivered for submission {}", submissionId);
    }

    public String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }
}
