package com.judge.api.dto;

import com.judge.domain.TestCase;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestCaseResponse {
    private Long id;
    private boolean isSample;
    private int score;
    private int orderIndex;

    public static TestCaseResponse from(TestCase tc) {
        return TestCaseResponse.builder()
                .id(tc.getId())
                .isSample(tc.isSample())
                .score(tc.getScore())
                .orderIndex(tc.getOrderIndex())
                .build();
    }
}
