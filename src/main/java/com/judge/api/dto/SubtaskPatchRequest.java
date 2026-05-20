package com.judge.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubtaskPatchRequest {
    @Size(max = 100)
    private String name;
    @Min(0)
    private Integer score;
}
