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
    private String descriptionFormat;
    private int timeLimitMs;
    private int memoryLimitKb;
    private boolean isPublished;
    private String checkerType;
    private String checkerLanguage;
    private boolean hasChecker;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProblemResponse from(Problem p) {
        return ProblemResponse.builder()
                .id(p.getId())
                .slug(p.getSlug())
                .title(p.getTitle())
                .description(p.getDescription())
                .descriptionFormat(p.getDescriptionFormat())
                .timeLimitMs(p.getTimeLimitMs())
                .memoryLimitKb(p.getMemoryLimitKb())
                .isPublished(p.isPublished())
                .checkerType(p.getCheckerType())
                .checkerLanguage(p.getCheckerLanguage())
                .hasChecker(p.getCheckerBinPath() != null && !p.getCheckerBinPath().isBlank())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
