package com.judge.api.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ScoreboardEntry {
    private int rank;
    private String userRef;
    private int totalScore;
    private long totalPenaltyMinutes;
    private List<ProblemScore> problems;

    @Data
    @Builder
    public static class ProblemScore {
        private String alias;
        private Long problemId;
        private int score;
        private int wrongAttempts;
        private Long minutesSinceStart;
        private boolean solved;
    }
}
