package com.judge.repository;

import com.judge.domain.Subtask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubtaskRepository extends JpaRepository<Subtask, Long> {
    List<Subtask> findByProblemIdOrderByOrderIndexAsc(Long problemId);
    void deleteByProblemId(Long problemId);
}
