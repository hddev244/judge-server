package com.judge.judge.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RunResult {
    private final String stdout;
    private final String stderr;
    private final int exitCode;
    /** CPU time (user+sys) in ms — this is what the TLE verdict and reported time use. */
    private final long timeMs;
    /** Wall-clock time inside the container in ms (diagnostic only). */
    private final long wallTimeMs;
    /** Peak resident set size in KB, from GNU time. */
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

    public static RunResult mle(long memoryKb) {
        return RunResult.builder()
                .memoryExceeded(true)
                .exitCode(137)
                .memoryKb(memoryKb)
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
