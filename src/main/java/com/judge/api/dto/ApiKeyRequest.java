package com.judge.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ApiKeyRequest {
    @NotBlank
    private String clientName;

    @Min(1) @Max(10000)
    private int rateLimitPerHour = 100;

    private boolean isAdmin = false;
}
