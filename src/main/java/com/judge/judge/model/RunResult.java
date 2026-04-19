package com.judge.judge.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RunResult {
    private final String stdout;
    private final String stderr;
    private final int exitCode;
    private final long timeMs;
    private final long memoryKb;
    private final boolean timedOut;
    private final boolean memoryExceeded;
    private final boolean systemError;

    public static RunResult tle(long timeMs) {
        return RunResult.builder()
                .timedOut(true)
                .exitCode(124)
                .timeMs(timeMs)
                .stdout("")
                .stderr("Time Limit Exceeded")
                .build();
    }

    public static RunResult mle() {
        return RunResult.builder()
                .memoryExceeded(true)
                .exitCode(137)
                .stdout("")
                .stderr("Memory Limit Exceeded")
                .build();
    }

    public static RunResult dockerUnavailable(String message) {
        return RunResult.builder()
                .systemError(true)
                .exitCode(-1)
                .stdout("")
                .stderr(message)
                .build();
    }
}
