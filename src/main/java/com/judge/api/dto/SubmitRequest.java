package com.judge.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class SubmitRequest {
    @NotNull
    private Long problemId;

    @NotBlank
    @Pattern(regexp = "cpp|java|python", message = "language must be cpp, java, or python")
    private String language;

    @NotBlank
    private String sourceCode;

    private String userRef;

    @URL(message = "callbackUrl must be a valid URL")
    private String callbackUrl;
}
