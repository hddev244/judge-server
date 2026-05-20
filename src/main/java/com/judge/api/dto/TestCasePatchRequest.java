package com.judge.api.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class TestCasePatchRequest {
    @Min(0)
    private Integer score;
    private Boolean isSample;
    private Long subtaskId;
    private boolean clearSubtask = false;
}
