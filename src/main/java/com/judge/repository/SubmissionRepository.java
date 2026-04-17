package com.judge.repository;

import com.judge.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, String> {
    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.results r LEFT JOIN FETCH r.testCase WHERE s.id = :id")
    Optional<Submission> findByIdWithResults(String id);
}
