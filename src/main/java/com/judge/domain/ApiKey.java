package com.judge.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_keys")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String key;

    @Column(name = "client_name", nullable = false, length = 100)
    private String clientName;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "is_admin", nullable = false)
    private boolean isAdmin;

    @Column(name = "rate_limit_per_hour", nullable = false)
    private int rateLimitPerHour;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
