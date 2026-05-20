package com.judge.api.dto;

import com.judge.domain.TestCase;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestCaseDetailResponse {
    private Long id;
    private boolean isSample;
    private int score;
    private int orderIndex;
    private Long subtaskId;
    private String inputContent;
    private String outputContent;

    public static TestCaseDetailResponse from(TestCase tc, String inputContent, String outputContent) {
        return TestCaseDetailResponse.builder()
                .id(tc.getId())
                .isSample(tc.isSample())
                .score(tc.getScore())
                .orderIndex(tc.getOrderIndex())
                .subtaskId(tc.getSubtask() != null ? tc.getSubtask().getId() : null)
                .inputContent(inputContent)
                .outputContent(outputContent)
                .build();
    }
}
