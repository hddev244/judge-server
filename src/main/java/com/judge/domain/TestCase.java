package com.judge.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "test_cases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "input_path", nullable = false, length = 500)
    private String inputPath;

    @Column(name = "output_path", nullable = false, length = 500)
    private String outputPath;

    @Column(name = "is_sample", nullable = false)
    private boolean isSample;

    @Column(nullable = false)
    private int score;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;
}
