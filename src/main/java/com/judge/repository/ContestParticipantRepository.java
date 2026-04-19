package com.judge.repository;

import com.judge.domain.ContestParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ContestParticipantRepository extends JpaRepository<ContestParticipant, Long> {
    Optional<ContestParticipant> findByContestIdAndUserRef(Long contestId, String userRef);
    boolean existsByContestIdAndUserRef(Long contestId, String userRef);
    List<ContestParticipant> findByContestId(Long contestId);
}
