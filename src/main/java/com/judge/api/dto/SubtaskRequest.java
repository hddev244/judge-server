package com.judge.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubtaskRequest {
    @NotBlank
    private String name;

    @Min(0)
    private int score;

    private int orderIndex;
}
