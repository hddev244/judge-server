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
    private String input;
    private String output;
    private String inputContent;
    private String outputContent;

    public String getEffectiveInput() {
        return input != null ? input : inputContent;
    }

    public String getEffectiveOutput() {
        return output != null ? output : outputContent;
    }
}
