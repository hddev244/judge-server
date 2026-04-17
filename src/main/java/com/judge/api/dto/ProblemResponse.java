package com.judge.api.dto;

import com.judge.domain.Problem;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProblemResponse {
    private Long id;
    private String slug;
    private String title;
    private String description;
    private int timeLimitMs;
    private int memoryLimitKb;
    private boolean isPublished;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProblemResponse from(Problem p) {
        return ProblemResponse.builder()
                .id(p.getId())
                .slug(p.getSlug())
                .title(p.getTitle())
                .description(p.getDescription())
                .timeLimitMs(p.getTimeLimitMs())
                .memoryLimitKb(p.getMemoryLimitKb())
                .isPublished(p.isPublished())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
