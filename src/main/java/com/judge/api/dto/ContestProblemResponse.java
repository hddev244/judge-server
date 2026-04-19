package com.judge.api.dto;

import com.judge.domain.ContestProblem;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContestProblemResponse {
    private Long id;
    private Long problemId;
    private String slug;
    private String title;
    private int orderIndex;
    private String alias;

    public static ContestProblemResponse from(ContestProblem cp) {
        return ContestProblemResponse.builder()
                .id(cp.getId())
                .problemId(cp.getProblem().getId())
                .slug(cp.getProblem().getSlug())
                .title(cp.getProblem().getTitle())
                .orderIndex(cp.getOrderIndex())
                .alias(cp.getAlias())
                .build();
    }
}
