package com.judge.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "problems")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Problem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "description_format", length = 10, nullable = false)
    @Builder.Default
    private String descriptionFormat = "MARKDOWN";

    @Column(name = "time_limit_ms", nullable = false)
    private int timeLimitMs;

    @Column(name = "memory_limit_kb", nullable = false)
    private int memoryLimitKb;

    @Column(name = "is_published", nullable = false)
    private boolean isPublished;

    /** EXACT (default) or CUSTOM */
    @Column(name = "checker_type", nullable = false, length = 20)
    @Builder.Default
    private String checkerType = "EXACT";

    @Column(name = "checker_language", length = 10)
    private String checkerLanguage;

    @Column(name = "checker_source", columnDefinition = "TEXT")
    private String checkerSource;

    @Column(name = "checker_bin_path", length = 500)
    private String checkerBinPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "problem", fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<TestCase> testCases;

    @PrePersist
    void prePersist() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }
}
