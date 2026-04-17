package com.judge.api.dto;

import com.judge.domain.Submission;
import com.judge.domain.SubmissionResult;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
public class SubmissionResponse {
    private String submissionId;
    private String status;
    private int score;
    private Integer timeMs;
    private Integer memoryKb;
    private String errorMessage;
    private String language;
    private String sourceCode;
    private boolean testRun;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private List<TestResultDto> testResults;

    public static SubmissionResponse from(Submission s) {
        List<TestResultDto> results = s.getResults() == null ? Collections.emptyList() :
                s.getResults().stream().map(r -> TestResultDto.builder()
                        .testCaseId(r.getTestCase().getId())
                        .status(r.getStatus())
                        .timeMs(r.getTimeMs())
                        .memoryKb(r.getMemoryKb())
                        .build()).toList();

        return SubmissionResponse.builder()
                .submissionId(s.getId())
                .status(s.getStatus())
                .score(s.getScore())
                .timeMs(s.getTimeMs())
                .memoryKb(s.getMemoryKb())
                .errorMessage(s.getErrorMessage())
                .language(s.getLanguage())
                .sourceCode(s.getSourceCode())
                .testRun(s.isTestRun())
                .createdAt(s.getCreatedAt())
                .finishedAt(s.getFinishedAt())
                .testResults(results)
                .build();
    }

    @Data
    @Builder
    public static class TestResultDto {
        private Long testCaseId;
        private String status;
        private Integer timeMs;
        private Integer memoryKb;
    }
}
