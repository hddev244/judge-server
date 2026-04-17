package com.judge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<Long, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var apiKey = ApiKeyContext.get();
        if (apiKey == null) {
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(apiKey.getId(), id ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(apiKey.getRateLimitPerHour())
                                .refillGreedy(apiKey.getRateLimitPerHour(), Duration.ofHours(1))
                                .build())
                        .build()
        );

        var probe = bucket.tryConsumeAndReturnRemaining(1);
        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        response.setHeader("X-RateLimit-Reset",
                Instant.now().plusSeconds(probe.getNanosToWaitForRefill() / 1_000_000_000).toString());

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "error", "RATE_LIMIT_EXCEEDED",
                    "limit", apiKey.getRateLimitPerHour(),
                    "resetAt", response.getHeader("X-RateLimit-Reset")
            ));
        }
    }
}
