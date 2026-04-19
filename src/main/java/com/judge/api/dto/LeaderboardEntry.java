package com.judge.api.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class LeaderboardEntry {
    private int rank;
    private String userRef;
    private long solvedCount;
    private long totalSubmissions;
    private double acceptanceRate;
    private LocalDateTime lastSolvedAt;
}
