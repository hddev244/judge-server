package com.judge.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contest_participants")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ContestParticipant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @Column(name = "user_ref", nullable = false, length = 100)
    private String userRef;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @PrePersist
    void prePersist() { registeredAt = LocalDateTime.now(); }
}
