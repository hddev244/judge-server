package com.judge.judge;

import com.judge.domain.Submission;
import lombok.Builder;
import lombok.Data;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JudgeStatusPublisher {

    private final SimpMessagingTemplate messaging;

    public JudgeStatusPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void publishPartial(String submissionId, List<TestCaseUpdate> results) {
        messaging.convertAndSend(
                "/topic/submissions/" + submissionId,
                Update.builder()
                        .submissionId(submissionId)
                        .status("JUDGING")
                        .testResults(results)
                        .build());
    }

    public void publishFinal(Submission sub, List<TestCaseUpdate> results) {
        messaging.convertAndSend(
                "/topic/submissions/" + sub.getId(),
                Update.builder()
                        .submissionId(sub.getId())
                        .status(sub.getStatus())
                        .score(sub.getScore())
                        .timeMs(sub.getTimeMs())
                        .errorMessage(sub.getErrorMessage())
                        .testResults(results)
                        .build());
    }

    @Data
    @Builder
    public static class Update {
        private String submissionId;
        private String status;
        private Integer score;
        private Integer timeMs;
        private String errorMessage;
        private List<TestCaseUpdate> testResults;
    }

    @Data
    @Builder
    public static class TestCaseUpdate {
        private Long testCaseId;
        private String status;
        private int timeMs;
        private int memoryKb;
    }
}
