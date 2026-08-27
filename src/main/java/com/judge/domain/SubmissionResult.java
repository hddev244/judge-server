package com.judge.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "submission_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id", nullable = true)
    private TestCase testCase;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "time_ms")
    private Integer timeMs;

    @Column(name = "memory_kb")
    private Integer memoryKb;

    /** Fraction of the case score awarded (0..1). NULL treated as 1.0 for AC. */
    @Column(name = "score_ratio")
    private Double scoreRatio;

    /** Truncated program stdout (see SubmissionPersistenceService.STDOUT_CAP). */
    @Column(columnDefinition = "TEXT")
    private String stdout;

    /** Truncated program stderr (RE / diagnostics). */
    @Column(columnDefinition = "TEXT")
    private String stderr;
}
