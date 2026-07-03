package com.judge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-API-key rate limiting backed by Redis (bucket4j). Buckets live in Redis so
 * limits survive restarts and are shared across replicas. If Redis is
 * unreachable we fail open (availability over strictness for a judge).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ProxyManager<byte[]> proxyManager;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ProxyManager<byte[]> proxyManager, ObjectMapper objectMapper) {
        this.proxyManager = proxyManager;
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

        int limit = apiKey.getRateLimitPerHour();
        byte[] key = ("rl:" + apiKey.getId()).getBytes(StandardCharsets.UTF_8);
        Supplier<BucketConfiguration> config = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit)
                        .refillGreedy(limit, Duration.ofHours(1))
                        .build())
                .build();

        ConsumptionProbe probe;
        try {
            probe = proxyManager.builder().build(key, config).tryConsumeAndReturnRemaining(1);
        } catch (Exception e) {
            // Redis down — don't block judging traffic.
            log.warn("Rate limiter unavailable, failing open: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

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
                    "limit", limit,
                    "resetAt", response.getHeader("X-RateLimit-Reset")
            ));
        }
    }
}
