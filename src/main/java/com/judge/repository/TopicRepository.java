package com.judge.repository;

import com.judge.domain.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    Optional<Topic> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsByName(String name);
    List<Topic> findAllByOrderByNameAsc();
}
