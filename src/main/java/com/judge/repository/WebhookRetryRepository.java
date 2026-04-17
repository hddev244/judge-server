package com.judge.repository;

import com.judge.domain.WebhookRetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface WebhookRetryRepository extends JpaRepository<WebhookRetry, Long> {
    @Query("SELECT r FROM WebhookRetry r WHERE r.failedPermanently = false AND r.nextRetryAt <= :now AND r.attemptCount < 4")
    List<WebhookRetry> findPendingRetries(LocalDateTime now);
}
