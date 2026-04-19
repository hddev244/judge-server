package com.judge.repository;

import com.judge.domain.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

public interface ProblemRepository extends JpaRepository<Problem, Long>,
        JpaSpecificationExecutor<Problem> {
    Optional<Problem> findBySlugAndIsPublishedTrue(String slug);
    Optional<Problem> findBySlug(String slug);
    List<Problem> findByIsPublishedTrueOrderByIdAsc();
    List<Problem> findAllByOrderByIdAsc();
}
