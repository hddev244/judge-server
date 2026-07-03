package com.judge.judge.model;

/** Result of an interactive judging run (solution ⇄ interactor over pipes). */
public record InteractiveResult(
        String verdict, double ratio,
        long timeMs, long memoryKb,
        boolean systemError) {}
