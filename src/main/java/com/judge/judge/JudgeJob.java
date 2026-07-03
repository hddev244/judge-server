package com.judge.judge;

import java.util.List;

/**
 * Immutable, fully-detached snapshot of everything the judge loop needs, built
 * inside a short transaction so the long docker phase holds no DB connection and
 * touches no lazy associations.
 */
public record JudgeJob(
        String submissionId,
        String language,
        String sourceCode,
        Long problemId,
        int timeLimitMs,
        int memoryLimitKb,
        String checkerType,
        String checkerLanguage,
        String checkerBinPath,
        String judgingMode,
        List<TestCaseView> testCases,
        List<SubtaskView> subtasks
) {
    public record TestCaseView(
            Long id, String inputPath, String outputPath,
            int score, boolean sample, Long subtaskId) {}

    public record SubtaskView(Long id, String name, int score) {}
}
