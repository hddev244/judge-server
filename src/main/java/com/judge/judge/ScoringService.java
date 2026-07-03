package com.judge.judge;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Pure scoring: turns per-test-case score ratios into a total. Subtask cases use
 * min-ratio aggregation (standard IOI); this reduces to all-or-nothing when
 * ratios are 0/1 as in exact checking. Loose cases score individually.
 */
@Service
public class ScoringService {

    /**
     * @param caseRatios ratio (0..1) awarded per test-case id; a missing id counts as 0
     */
    public int total(List<JudgeJob.SubtaskView> subtasks,
                      List<JudgeJob.TestCaseView> testCases,
                      Map<Long, Double> caseRatios) {
        int score = 0;
        for (JudgeJob.SubtaskView st : subtasks) {
            double min = 1.0;
            boolean hasCase = false;
            for (JudgeJob.TestCaseView tc : testCases) {
                if (st.id().equals(tc.subtaskId())) {
                    hasCase = true;
                    min = Math.min(min, caseRatios.getOrDefault(tc.id(), 0.0));
                }
            }
            if (hasCase) score += (int) Math.round(st.score() * min);
        }
        for (JudgeJob.TestCaseView tc : testCases) {
            if (tc.subtaskId() == null) {
                score += (int) Math.round(tc.score() * caseRatios.getOrDefault(tc.id(), 0.0));
            }
        }
        return score;
    }
}
