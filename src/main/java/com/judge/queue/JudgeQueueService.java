package com.judge.queue;

import com.judge.config.JudgeConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class JudgeQueueService {

    private static final String LOCK_PREFIX = "judge:lock:";
    /** Lock lifetime; the worker renews it every ~1/3 of this while judging. */
    static final Duration LOCK_TTL = Duration.ofSeconds(60);

    // Compare-and-set on the lock token so we never renew/release a lock a
    // different worker acquired after ours expired.
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) else return 0 end", Long.class);

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

    /** SETNX with a per-attempt token. Returns the token if acquired, else empty. */
    public Optional<String> tryLock(String submissionId) {
        String token = java.util.UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(LOCK_PREFIX + submissionId, token, LOCK_TTL);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    /** Extends the lock TTL iff we still own it. */
    public boolean renewLock(String submissionId, String token) {
        Long r = redis.execute(RENEW, List.of(LOCK_PREFIX + submissionId),
                token, String.valueOf(LOCK_TTL.toMillis()));
        return r != null && r == 1L;
    }

    /** Deletes the lock iff we still own it. */
    public void releaseLock(String submissionId, String token) {
        redis.execute(RELEASE, List.of(LOCK_PREFIX + submissionId), token);
    }

    /** True if a submission currently has a live lock (used by the stuck-job watchdog). */
    public boolean isLocked(String submissionId) {
        return Boolean.TRUE.equals(redis.hasKey(LOCK_PREFIX + submissionId));
    }
}
