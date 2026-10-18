package com.judge.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TestRunRequest {
    @NotNull
    private Long problemId;

    @NotBlank
    @Pattern(regexp = "cpp|java|python|pascal", message = "language must be cpp, java, python, or pascal")
    private String language;

    @NotBlank
    private String sourceCode;

    /**
     * Optional stdin for a one-shot run (no expected-output compare).
     * When blank, the test endpoint runs the problem's sample cases instead.
     */
    private String input;
}
