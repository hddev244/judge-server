package com.judge.api.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class UserStatsResponse {
    private String userRef;
    private long solvedCount;
    private long totalSubmissions;
    private double acceptanceRate;
    private LocalDateTime lastSolvedAt;
    private List<SolvedProblem> solvedProblems;
    private Map<String, Long> languageBreakdown;
    private List<RecentSubmission> recentSubmissions;

    @Data
    @Builder
    public static class SolvedProblem {
        private Long id;
        private String slug;
        private String title;
        private LocalDateTime solvedAt;
    }

    @Data
    @Builder
    public static class RecentSubmission {
        private String id;
        private String problemSlug;
        private String language;
        private String status;
        private int score;
        private LocalDateTime createdAt;
    }
}
