package com.judge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.judge.domain.Contest;
import com.judge.domain.ContestProblem;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ContestResponse {
    private Long id;
    private String slug;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @JsonProperty("isPublic")
    private boolean isPublic;
    private String status;   // UPCOMING | ONGOING | ENDED
    private List<ContestProblemResponse> problems;
    private LocalDateTime createdAt;

    public static ContestResponse from(Contest c, List<ContestProblem> problems) {
        LocalDateTime now = LocalDateTime.now();
        String status = now.isBefore(c.getStartTime()) ? "UPCOMING"
                      : now.isAfter(c.getEndTime())    ? "ENDED"
                      :                                  "ONGOING";
        return ContestResponse.builder()
                .id(c.getId())
                .slug(c.getSlug())
                .title(c.getTitle())
                .description(c.getDescription())
                .startTime(c.getStartTime())
                .endTime(c.getEndTime())
                .isPublic(c.isPublic())
                .status(status)
                .problems(problems.stream().map(ContestProblemResponse::from).toList())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
