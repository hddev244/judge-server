package com.judge.repository;

import com.judge.domain.Contest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {
    Optional<Contest> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Contest> findByIsPublicTrueAndEndTimeAfterOrderByStartTimeAsc(LocalDateTime now);
}
