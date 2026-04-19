package com.judge.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "contest_problems")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ContestProblem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(length = 10)
    private String alias;
}
