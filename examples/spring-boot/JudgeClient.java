package com.example.judge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Drop-in client for judge-server.
 *
 * application.yml:
 *   judge:
 *     server-url: http://localhost:8080
 *     api-key: sk_your_key
 *
 * Usage:
 *   SubmissionResult result = judgeClient.submitAndWait(1L, "cpp", sourceCode);
 *   System.out.println(result.status()); // "AC", "WA", "TLE", ...
 */
@Component
public class JudgeClient {

    private final RestTemplate restTemplate;
    private final String serverUrl;
    private final String apiKey;

    public JudgeClient(
            @Value("${judge.server-url}") String serverUrl,
            @Value("${judge.api-key}") String apiKey) {
        this.restTemplate = new RestTemplate();
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Submit asynchronously and return the submissionId immediately.
     * The submission will be judged in the background.
     */
    public String submitAsync(Long problemId, String language, String sourceCode) {
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "problemId", problemId,
                "language", language,
                "sourceCode", sourceCode
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                serverUrl + "/api/v1/submissions",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        if (response.getBody() == null) {
            throw new RuntimeException("Empty response from judge server");
        }
        return (String) response.getBody().get("submissionId");
    }

    /**
     * Poll the result of a submission by its ID.
     */
    public SubmissionResult getResult(String submissionId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                serverUrl + "/api/v1/submissions/" + submissionId,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                Map.class
        );

        if (response.getBody() == null) {
            throw new RuntimeException("Empty response for submission " + submissionId);
        }
        return mapToResult(response.getBody());
    }

    /**
     * Submit and block until a terminal verdict is returned (or 60s timeout).
     *
     * @throws RuntimeException if timeout is exceeded
     */
    public SubmissionResult submitAndWait(Long problemId, String language, String sourceCode) {
        String submissionId = submitAsync(problemId, language, sourceCode);

        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            SubmissionResult result = getResult(submissionId);
            if (!isPending(result.status())) {
                return result;
            }
            sleep(1500);
        }
        throw new RuntimeException(
                "Timed out waiting for verdict on submission " + submissionId);
    }

    // ── Result record ─────────────────────────────────────────────────────────

    public record SubmissionResult(
            String submissionId,
            String status,
            int score,
            Integer timeMs,
            Integer memoryKb,
            String errorMessage
    ) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        return headers;
    }

    private static boolean isPending(String status) {
        return "PENDING".equals(status) || "JUDGING".equals(status);
    }

    private static SubmissionResult mapToResult(Map<?, ?> body) {
        return new SubmissionResult(
                (String) body.get("submissionId"),
                (String) body.get("status"),
                body.get("score") instanceof Number n ? n.intValue() : 0,
                body.get("timeMs") instanceof Number n ? n.intValue() : null,
                body.get("memoryKb") instanceof Number n ? n.intValue() : null,
                (String) body.get("errorMessage")
        );
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for verdict", e);
        }
    }
}
