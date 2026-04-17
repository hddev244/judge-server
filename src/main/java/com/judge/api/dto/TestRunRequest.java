package com.judge.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TestRunRequest {
    @NotNull
    private Long problemId;

    @NotBlank
    @Pattern(regexp = "cpp|java|python", message = "language must be cpp, java, or python")
    private String language;

    @NotBlank
    private String sourceCode;
}
