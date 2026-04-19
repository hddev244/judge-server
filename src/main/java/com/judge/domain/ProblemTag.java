package com.judge.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problem_tags")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProblemTag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false, length = 50)
    private String tag;
}
