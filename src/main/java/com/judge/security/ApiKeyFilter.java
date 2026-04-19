package com.judge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.judge.domain.ApiKey;
import com.judge.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ApiKeyFilter extends OncePerRequestFilter {

    private static final List<String> EXCLUDED = List.of(
            "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/docs.html", "/admin.html", "/solve.html", "/languages.html",
            "/api/v1/leaderboard", "/api/v1/users/*/stats",
            "/ws/**"
    );

    private final ApiKeyRepository apiKeyRepository;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public ApiKeyFilter(ApiKeyRepository apiKeyRepository, ObjectMapper objectMapper) {
        this.apiKeyRepository = apiKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED.stream().anyMatch(p -> matcher.match(p, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String rawKey = request.getHeader("X-API-Key");
        Optional<ApiKey> apiKey = rawKey != null
                ? apiKeyRepository.findByKeyAndIsActiveTrue(rawKey)
                : Optional.empty();

        if (apiKey.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                    Map.of("error", "UNAUTHORIZED", "message", "Invalid or missing API Key"));
            return;
        }

        try {
            ApiKeyContext.set(apiKey.get());
            chain.doFilter(request, response);
        } finally {
            ApiKeyContext.clear();
        }
    }
}
