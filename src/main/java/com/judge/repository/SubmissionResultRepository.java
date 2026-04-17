package com.judge.repository;

import com.judge.domain.SubmissionResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionResultRepository extends JpaRepository<SubmissionResult, Long> {
}
