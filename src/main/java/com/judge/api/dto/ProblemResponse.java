package com.judge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.judge.domain.Category;
import com.judge.domain.Problem;
import com.judge.domain.ProblemTag;
import com.judge.domain.Topic;
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
    private List<TopicInfo> topics;
    private List<CategoryInfo> categories;
    private Long solvedCount;
    private Double acceptanceRate;
    private String checkerType;
    private String checkerLanguage;
    private boolean hasChecker;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data @Builder
    public static class TopicInfo {
        private Long id;
        private String name;
        private String slug;
    }

    @Data @Builder
    public static class CategoryInfo {
        private Long id;
        private String name;
        private String slug;
    }

    public static ProblemResponse from(Problem p) {
        List<String> tags = p.getTags() != null
                ? p.getTags().stream().map(ProblemTag::getTag).toList()
                : List.of();
        List<TopicInfo> topics = p.getTopics() != null
                ? p.getTopics().stream()
                    .map(t -> TopicInfo.builder().id(t.getId()).name(t.getName()).slug(t.getSlug()).build())
                    .sorted(java.util.Comparator.comparing(TopicInfo::getId))
                    .toList()
                : List.of();
        List<CategoryInfo> categories = p.getCategories() != null
                ? p.getCategories().stream()
                    .map(c -> CategoryInfo.builder().id(c.getId()).name(c.getName()).slug(c.getSlug()).build())
                    .sorted(java.util.Comparator.comparing(CategoryInfo::getId))
                    .toList()
                : List.of();
        return from(p, tags, topics, categories);
    }

    public static ProblemResponse from(Problem p, List<String> tags) {
        return from(p, tags, List.of(), List.of());
    }

    public static ProblemResponse from(Problem p, List<String> tags,
                                        List<TopicInfo> topics, List<CategoryInfo> categories) {
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
                .topics(topics)
                .categories(categories)
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
