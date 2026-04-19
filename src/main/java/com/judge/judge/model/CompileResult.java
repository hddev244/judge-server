package com.judge.judge.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompileResult {
    private final boolean success;
    private final boolean systemError;
    private final String workDir;
    private final String errorOutput;
}
