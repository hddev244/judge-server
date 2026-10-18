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

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    @Column(name = "client_name", nullable = false, length = 100)
    private String clientName;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "is_admin", nullable = false)
    private boolean isAdmin;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private ApiKeyRole role = ApiKeyRole.CLIENT;

    @Column(name = "rate_limit_per_hour", nullable = false)
    private int rateLimitPerHour;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isGrader() {
        return role == ApiKeyRole.GRADER;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        syncAdminFlag();
    }

    @PreUpdate
    void preUpdate() {
        syncAdminFlag();
    }

    private void syncAdminFlag() {
        // Legacy callers only set isAdmin(true); never let that silently downgrade to CLIENT.
        if (role == null || (isAdmin && role == ApiKeyRole.CLIENT)) {
            role = isAdmin ? ApiKeyRole.ADMIN : ApiKeyRole.CLIENT;
        }
        isAdmin = role == ApiKeyRole.ADMIN;
    }
}
