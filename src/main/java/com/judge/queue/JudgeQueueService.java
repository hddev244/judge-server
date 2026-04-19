package com.judge.queue;

import com.judge.config.JudgeConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class JudgeQueueService {

    private static final String LOCK_PREFIX = "judge:lock:";

    private final StringRedisTemplate redis;
    private final JudgeConfig judgeConfig;

    public JudgeQueueService(StringRedisTemplate redis, JudgeConfig judgeConfig) {
        this.redis = redis;
        this.judgeConfig = judgeConfig;
    }

    public void enqueue(String submissionId) {
        redis.opsForList().leftPush(judgeConfig.getQueueKey(), submissionId);
    }

    /** Blocking pop (BRPOP) — atomic, only one worker gets each item. */
    public Optional<String> dequeue() {
        String result = redis.opsForList().rightPop(judgeConfig.getQueueKey(), Duration.ofSeconds(2));
        return Optional.ofNullable(result);
    }

    /**
     * SETNX with 10-minute TTL. Returns true if this worker acquired the lock,
     * false if another worker is already processing this submission.
     */
    public boolean tryLock(String submissionId) {
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(LOCK_PREFIX + submissionId, "1", Duration.ofMinutes(10));
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseLock(String submissionId) {
        redis.delete(LOCK_PREFIX + submissionId);
    }
}
