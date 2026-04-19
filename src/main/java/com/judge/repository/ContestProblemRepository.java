package com.judge.repository;

import com.judge.domain.ContestProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ContestProblemRepository extends JpaRepository<ContestProblem, Long> {
    List<ContestProblem> findByContestIdOrderByOrderIndexAsc(Long contestId);
    Optional<ContestProblem> findByContestIdAndProblemId(Long contestId, Long problemId);
    boolean existsByContestIdAndProblemId(Long contestId, Long problemId);
}
