package com.judge.service;

import com.judge.api.dto.LeaderboardEntry;
import com.judge.api.dto.UserStatsResponse;
import com.judge.domain.Submission;
import com.judge.exception.JudgeException;
import com.judge.repository.SubmissionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaderboardService {

    private final SubmissionRepository submissionRepository;

    public LeaderboardService(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Cacheable(value = "leaderboard", key = "#limit + '_' + #offset")
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> getLeaderboard(int limit, int offset) {
        List<Object[]> rows = submissionRepository.findLeaderboard(limit, offset);
        int rank = offset + 1;
        List<LeaderboardEntry> entries = new java.util.ArrayList<>();
        for (Object[] row : rows) {
            entries.add(LeaderboardEntry.builder()
                    .rank(rank++)
                    .userRef((String) row[0])
                    .solvedCount(toLong(row[1]))
                    .totalSubmissions(toLong(row[2]))
                    .acceptanceRate(toDouble(row[3]))
                    .lastSolvedAt(toDateTime(row[4]))
                    .build());
        }
        return entries;
    }

    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats(String userRef) {
        List<Object[]> statRows = submissionRepository.findUserStats(userRef);
        Object[] stats = statRows.isEmpty() ? null : statRows.get(0);
        if (stats == null || toLong(stats[1]) == 0) {
            throw JudgeException.notFound("User not found: " + userRef);
        }

        List<UserStatsResponse.SolvedProblem> solved = submissionRepository
                .findSolvedProblems(userRef)
                .stream()
                .map(r -> UserStatsResponse.SolvedProblem.builder()
                        .id(toLong(r[0]))
                        .slug((String) r[1])
                        .title((String) r[2])
                        .solvedAt(toDateTime(r[3]))
                        .build())
                .toList();

        Map<String, Long> langBreakdown = new LinkedHashMap<>();
        submissionRepository.findLanguageBreakdown(userRef)
                .forEach(r -> langBreakdown.put((String) r[0], toLong(r[1])));

        List<UserStatsResponse.RecentSubmission> recent = submissionRepository
                .findRecentByUserRef(userRef, PageRequest.of(0, 20))
                .stream()
                .map(s -> UserStatsResponse.RecentSubmission.builder()
                        .id(s.getId())
                        .problemSlug(s.getProblem().getSlug())
                        .language(s.getLanguage())
                        .status(s.getStatus())
                        .score(s.getScore())
                        .createdAt(s.getCreatedAt())
                        .build())
                .toList();

        return UserStatsResponse.builder()
                .userRef(userRef)
                .solvedCount(toLong(stats[0]))
                .totalSubmissions(toLong(stats[1]))
                .acceptanceRate(toDouble(stats[2]))
                .lastSolvedAt(toDateTime(stats[3]))
                .solvedProblems(solved)
                .languageBreakdown(langBreakdown)
                .recentSubmissions(recent)
                .build();
    }

    private long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    private double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof BigDecimal bd) return bd.doubleValue();
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }

    private LocalDateTime toDateTime(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDateTime ldt) return ldt;
        if (o instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }
}
