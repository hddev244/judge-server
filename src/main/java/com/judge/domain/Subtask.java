package com.judge.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subtasks")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Subtask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int score;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;
}
