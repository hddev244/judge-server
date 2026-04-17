package com.judge.queue;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class JudgeQueueService {

    private static final String QUEUE_KEY = "judge:queue";
    private static final String PROCESSING_KEY = "judge:processing";

    private final StringRedisTemplate redis;

    public JudgeQueueService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void enqueue(String submissionId) {
        redis.opsForList().leftPush(QUEUE_KEY, submissionId);
    }

    public Optional<String> dequeue() {
        String result = redis.opsForList().rightPop(QUEUE_KEY, Duration.ofSeconds(2));
        return Optional.ofNullable(result);
    }

    public void markProcessing(String submissionId) {
        redis.opsForSet().add(PROCESSING_KEY, submissionId);
        redis.expire(PROCESSING_KEY, Duration.ofMinutes(5));
    }

    public void markDone(String submissionId) {
        redis.opsForSet().remove(PROCESSING_KEY, submissionId);
    }
}
