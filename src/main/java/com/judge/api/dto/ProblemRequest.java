package com.judge.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class ProblemRequest {
    @NotBlank
    @Pattern(regexp = "[a-z0-9-]+", message = "slug must be lowercase alphanumeric with hyphens")
    private String slug;

    @NotBlank
    private String title;

    private String description;

    @Pattern(regexp = "MARKDOWN|HTML", message = "descriptionFormat must be MARKDOWN or HTML")
    private String descriptionFormat = "MARKDOWN";

    @Min(100) @Max(10000)
    private int timeLimitMs = 2000;

    @Min(16384) @Max(1048576)
    private int memoryLimitKb = 262144;

    @Pattern(regexp = "easy|medium|hard", message = "difficulty must be easy, medium, or hard")
    private String difficulty;

    private List<String> tags;

    private List<Long> topicIds;

    private List<Long> categoryIds;

    /** null or empty = all languages allowed */
    private List<String> allowedLanguages;

    @Pattern(regexp = "EXACT|FLOAT", message = "comparisonMode must be EXACT or FLOAT")
    private String comparisonMode = "EXACT";

    private Double floatEpsilon;

    @Pattern(regexp = "ALL|STOP_ON_FIRST_FAIL|SUBTASK_SKIP",
            message = "judgingMode must be ALL, STOP_ON_FIRST_FAIL or SUBTASK_SKIP")
    private String judgingMode = "ALL";
}
