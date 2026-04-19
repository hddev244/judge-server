package com.judge.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContestProblemRequest {
    @NotNull
    private Long problemId;
    private int orderIndex;
    private String alias;
}
