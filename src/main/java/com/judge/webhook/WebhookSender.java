package com.judge.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.judge.api.dto.SubmissionResponse;
import com.judge.domain.Submission;
import com.judge.domain.WebhookRetry;
import com.judge.repository.WebhookRetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public WebhookSender(WebhookRetryRepository retryRepository,
                         ObjectMapper objectMapper) {
        this.retryRepository = retryRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @Async
    public void sendAsync(Submission submission) {
        if (submission.getCallbackUrl() == null) return;
        try {
            SubmissionResponse payload = SubmissionResponse.from(submission);
            String json = objectMapper.writeValueAsString(payload);
            String signature = sign(json, submission.getCallbackUrl());
            send(submission.getCallbackUrl(), json, signature, submission.getId());
        } catch (Exception e) {
            log.error("Failed to send webhook for {}", submission.getId(), e);
        }
    }

    public void send(String url, String json, String signature, String submissionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Judge-Signature", signature);
        headers.set("X-Judge-Submission-Id", submissionId);

        try {
            restTemplate.postForEntity(url, new HttpEntity<>(json, headers), Void.class);
            log.info("Webhook delivered for submission {}", submissionId);
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
