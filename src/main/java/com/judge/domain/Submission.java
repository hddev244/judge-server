package com.judge.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "submissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Submission {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_ref", length = 100)
    private String userRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = true)
    private Problem problem;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(name = "source_code", nullable = false, columnDefinition = "TEXT")
    private String sourceCode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int score;

    @Column(name = "time_ms")
    private Integer timeMs;

    @Column(name = "memory_kb")
    private Integer memoryKb;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    @Column(name = "is_test_run", nullable = false)
    private boolean isTestRun;

    @Column(name = "contest_id")
    private Long contestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ApiKey client;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "submission", fetch = FetchType.LAZY)
    private List<SubmissionResult> results;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
