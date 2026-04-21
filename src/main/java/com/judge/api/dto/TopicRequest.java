package com.judge.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TopicRequest {
    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "[a-z0-9-]+", message = "slug must be lowercase alphanumeric with hyphens")
    private String slug;

    private String description;
}
