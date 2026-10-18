package com.judge.api.dto;

import com.judge.domain.ApiKeyRole;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ApiKeyRequest {
    @NotBlank
    private String clientName;

    @Min(1) @Max(10000)
    private int rateLimitPerHour = 100;

    /** @deprecated use {@link #role}. Kept so existing scripts sending {@code isAdmin: true} still work. */
    @Deprecated
    private boolean isAdmin = false;

    /** Null → derived from {@link #isAdmin} (ADMIN or CLIENT). */
    private ApiKeyRole role;

    public ApiKeyRole resolvedRole() {
        if (role != null) return role;
        return isAdmin ? ApiKeyRole.ADMIN : ApiKeyRole.CLIENT;
    }
}
