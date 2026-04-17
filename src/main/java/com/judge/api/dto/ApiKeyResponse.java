package com.judge.api.dto;

import com.judge.domain.ApiKey;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ApiKeyResponse {
    private Long id;
    private String key;
    private String clientName;
    private boolean isActive;
    private boolean isAdmin;
    private int rateLimitPerHour;
    private LocalDateTime createdAt;

    public static ApiKeyResponse from(ApiKey k) {
        return ApiKeyResponse.builder()
                .id(k.getId())
                .key(k.getKey())
                .clientName(k.getClientName())
                .isActive(k.isActive())
                .isAdmin(k.isAdmin())
                .rateLimitPerHour(k.getRateLimitPerHour())
                .createdAt(k.getCreatedAt())
                .build();
    }
}
