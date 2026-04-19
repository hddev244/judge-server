package com.judge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ContestRequest {
    @NotBlank
    @Pattern(regexp = "[a-z0-9-]+", message = "slug must be lowercase alphanumeric with hyphens")
    private String slug;

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;

    @JsonProperty("isPublic")
    private boolean isPublic = false;
}
