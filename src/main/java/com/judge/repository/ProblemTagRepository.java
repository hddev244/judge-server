package com.judge.repository;

import com.judge.domain.ProblemTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemTagRepository extends JpaRepository<ProblemTag, Long> {
    List<ProblemTag> findByProblemId(Long problemId);
    void deleteByProblemId(Long problemId);
}
