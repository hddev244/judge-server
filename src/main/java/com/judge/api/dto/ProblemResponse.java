package com.judge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.judge.domain.Problem;
import com.judge.domain.ProblemTag;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
    @JsonProperty("isPublished")
    private boolean isPublished;
    private String difficulty;
    private List<String> tags;
    private Long solvedCount;
    private Double acceptanceRate;
    private String checkerType;
    private String checkerLanguage;
    private boolean hasChecker;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProblemResponse from(Problem p) {
        List<String> tags = p.getTags() != null
                ? p.getTags().stream().map(ProblemTag::getTag).toList()
                : List.of();
        return from(p, tags);
    }

    public static ProblemResponse from(Problem p, List<String> tags) {
        return ProblemResponse.builder()
                .id(p.getId())
                .slug(p.getSlug())
                .title(p.getTitle())
                .description(p.getDescription())
                .descriptionFormat(p.getDescriptionFormat())
                .timeLimitMs(p.getTimeLimitMs())
                .memoryLimitKb(p.getMemoryLimitKb())
                .isPublished(p.isPublished())
                .difficulty(p.getDifficulty())
                .tags(tags)
                .solvedCount(p.getSolvedCount())
                .acceptanceRate(p.getAcceptanceRate())
                .checkerType(p.getCheckerType())
                .checkerLanguage(p.getCheckerLanguage())
                .hasChecker(p.getCheckerBinPath() != null && !p.getCheckerBinPath().isBlank())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
