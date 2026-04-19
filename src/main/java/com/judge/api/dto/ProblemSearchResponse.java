package com.judge.api.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ProblemSearchResponse {
    private List<ProblemResponse> content;
    private long totalElements;
    private int page;
    private int size;
}
