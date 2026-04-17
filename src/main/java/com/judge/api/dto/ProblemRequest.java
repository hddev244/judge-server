package com.judge.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ProblemRequest {
    @NotBlank
    @Pattern(regexp = "[a-z0-9-]+", message = "slug must be lowercase alphanumeric with hyphens")
    private String slug;

    @NotBlank
    private String title;

    private String description;

    @Min(100) @Max(10000)
    private int timeLimitMs = 2000;

    @Min(16384) @Max(1048576)
    private int memoryLimitKb = 262144;
}
