package com.judge.judge;

import com.judge.judge.JudgeJob.SubtaskView;
import com.judge.judge.JudgeJob.TestCaseView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoringServiceTest {

    private final ScoringService scoring = new ScoringService();

    private TestCaseView tc(long id, int score, Long subtaskId) {
        return new TestCaseView(id, "in", "out", score, false, subtaskId);
    }

    @Test
    void looseCasesScoreIndividually() {
        var cases = List.of(tc(1, 40, null), tc(2, 60, null));
        int s = scoring.total(List.of(), cases, Map.of(1L, 1.0, 2L, 0.0));
        assertEquals(40, s);
    }

    @Test
    void subtaskIsAllOrNothingWithBinaryRatios() {
        var st = new SubtaskView(10L, "st1", 100);
        var cases = List.of(tc(1, 0, 10L), tc(2, 0, 10L));
        assertEquals(100, scoring.total(List.of(st), cases, Map.of(1L, 1.0, 2L, 1.0)));
        assertEquals(0, scoring.total(List.of(st), cases, Map.of(1L, 1.0, 2L, 0.0)));
    }

    @Test
    void subtaskUsesMinRatioForPartialCredit() {
        var st = new SubtaskView(10L, "st1", 100);
        var cases = List.of(tc(1, 0, 10L), tc(2, 0, 10L));
        // min(0.8, 0.5) = 0.5 -> 50
        assertEquals(50, scoring.total(List.of(st), cases, Map.of(1L, 0.8, 2L, 0.5)));
    }

    @Test
    void missingRatioCountsAsZero() {
        var cases = List.of(tc(1, 100, null));
        assertEquals(0, scoring.total(List.of(), cases, Map.of()));
    }

    @Test
    void mixesSubtaskAndLooseCases() {
        var st = new SubtaskView(10L, "st1", 60);
        var cases = List.of(tc(1, 0, 10L), tc(2, 0, 10L), tc(3, 40, null));
        int s = scoring.total(List.of(st), cases, Map.of(1L, 1.0, 2L, 1.0, 3L, 1.0));
        assertEquals(100, s);
    }
}
