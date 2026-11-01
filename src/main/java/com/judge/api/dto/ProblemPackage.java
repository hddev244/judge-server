package com.judge.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Self-contained, lossless description of a problem's grading data: limits, comparison/judging
 * modes, checker, subtasks and every test case (with its own score). Exported by an admin key on
 * the source judge and applied verbatim by a GRADER key on a replica, so both judges grade the
 * same submission identically. Unlike the ZIP import, per-test scores are preserved.
 *
 * <p>The file I/O fields are omitted when null (STDIO), so packages of ordinary problems keep the
 * exact JSON — and therefore hash — they had before file I/O existed.
 */
public record ProblemPackage(
        @NotBlank @Size(max = 255) String title,
        String description,
        @Pattern(regexp = "MARKDOWN|HTML") String descriptionFormat,
        @Min(100) @Max(60_000) int timeLimitMs,
        @Min(16_384) @Max(4_194_304) int memoryLimitKb,
        @Pattern(regexp = "easy|medium|hard") String difficulty,
        List<String> tags,
        @Pattern(regexp = "EXACT|FLOAT") String comparisonMode,
        Double floatEpsilon,
        @Pattern(regexp = "ALL|STOP_ON_FIRST_FAIL|SUBTASK_SKIP") String judgingMode,
        @Size(max = 100) String allowedLanguages,
        @Valid Checker checker,
        @Valid List<SubtaskSpec> subtasks,
        @NotEmpty @Valid List<TestCaseSpec> testCases,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Pattern(regexp = "STDIO|FILE|FILE_OR_STDIO") String ioMode,
        @JsonInclude(JsonInclude.Include.NON_NULL) String inputFile,
        @JsonInclude(JsonInclude.Include.NON_NULL) String outputFile
) {

    /** Stdin/stdout package (no file I/O). */
    public ProblemPackage(String title, String description, String descriptionFormat,
                          int timeLimitMs, int memoryLimitKb, String difficulty, List<String> tags,
                          String comparisonMode, Double floatEpsilon, String judgingMode,
                          String allowedLanguages, Checker checker, List<SubtaskSpec> subtasks,
                          List<TestCaseSpec> testCases) {
        this(title, description, descriptionFormat, timeLimitMs, memoryLimitKb, difficulty, tags,
                comparisonMode, floatEpsilon, judgingMode, allowedLanguages, checker, subtasks,
                testCases, null, null, null);
    }

    public record Checker(
            @Pattern(regexp = "CUSTOM|INTERACTIVE") String type,
            @NotBlank String language,
            @NotBlank String source
    ) {}

    /** {@code key} is a package-local identifier that test cases reference via {@code subtaskKey}. */
    public record SubtaskSpec(
            @NotBlank @Size(max = 64) String key,
            @NotBlank @Size(max = 100) String name,
            @Min(0) int score,
            @Min(0) int orderIndex
    ) {}

    public record TestCaseSpec(
            @NotNull String input,
            @NotNull String output,
            boolean sample,
            @Min(0) int score,
            @Min(0) int orderIndex,
            String subtaskKey
    ) {}
}
