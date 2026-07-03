package com.judge.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.judge.domain.ApiKey;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ApiKeyResponse {
    private Long id;
    /** Raw key — only present in the response that creates the key. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String key;
    private String keyPrefix;
    private String clientName;
    private boolean isActive;
    private boolean isAdmin;
    private int rateLimitPerHour;
    private LocalDateTime createdAt;

    public static ApiKeyResponse from(ApiKey k) {
        return from(k, null);
    }

    public static ApiKeyResponse from(ApiKey k, String rawKey) {
        return ApiKeyResponse.builder()
                .id(k.getId())
                .key(rawKey)
                .keyPrefix(k.getKeyPrefix())
                .clientName(k.getClientName())
                .isActive(k.isActive())
                .isAdmin(k.isAdmin())
                .rateLimitPerHour(k.getRateLimitPerHour())
                .createdAt(k.getCreatedAt())
                .build();
    }
}
