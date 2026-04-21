package com.judge.api.dto;

import com.judge.domain.Category;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CategoryResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private int problemCount;
    private List<ProblemSummary> problems;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class ProblemSummary {
        private Long id;
        private String slug;
        private String title;
        private String difficulty;
    }

    public static CategoryResponse from(Category c, boolean includeProblems) {
        var builder = CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .description(c.getDescription())
                .problemCount(c.getProblems() == null ? 0 : c.getProblems().size())
                .createdAt(c.getCreatedAt());

        if (includeProblems && c.getProblems() != null) {
            builder.problems(c.getProblems().stream()
                    .map(p -> ProblemSummary.builder()
                            .id(p.getId()).slug(p.getSlug())
                            .title(p.getTitle()).difficulty(p.getDifficulty())
                            .build())
                    .sorted(java.util.Comparator.comparing(ProblemSummary::getId))
                    .toList());
        }
        return builder.build();
    }
}
