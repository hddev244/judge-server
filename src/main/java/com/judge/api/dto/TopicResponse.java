package com.judge.api.dto;

import com.judge.domain.Topic;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TopicResponse {
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

    public static TopicResponse from(Topic t, boolean includeProblems) {
        var builder = TopicResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .slug(t.getSlug())
                .description(t.getDescription())
                .problemCount(t.getProblems() == null ? 0 : t.getProblems().size())
                .createdAt(t.getCreatedAt());

        if (includeProblems && t.getProblems() != null) {
            builder.problems(t.getProblems().stream()
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
