package com.judge.api.dto;

import com.judge.domain.Subtask;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubtaskResponse {
    private Long id;
    private String name;
    private int score;
    private int orderIndex;

    public static SubtaskResponse from(Subtask s) {
        return SubtaskResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .score(s.getScore())
                .orderIndex(s.getOrderIndex())
                .build();
    }
}
