package com.judge.queue;

import com.judge.config.JudgeConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class JudgeQueueService {

    private static final String PROCESSING_KEY = "judge:processing";

    private final StringRedisTemplate redis;
    private final JudgeConfig judgeConfig;

    public JudgeQueueService(StringRedisTemplate redis, JudgeConfig judgeConfig) {
        this.redis = redis;
        this.judgeConfig = judgeConfig;
    }

    public void enqueue(String submissionId) {
        redis.opsForList().leftPush(judgeConfig.getQueueKey(), submissionId);
    }

    public Optional<String> dequeue() {
        String result = redis.opsForList().rightPop(judgeConfig.getQueueKey(), Duration.ofSeconds(2));
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
