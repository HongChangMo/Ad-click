package com.adclick.click.infrastructure;

import com.adclick.click.domain.ReconciliationLockPort;
import com.adclick.click.support.ValkeyCircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ValKeyReconciliationLockAdapter implements ReconciliationLockPort {

    private static final String KEY_PREFIX = "reconciliation:lock:";

    private final StringRedisTemplate redisTemplate;
    private final ValkeyCircuitBreaker circuitBreaker;
    private final Duration ttl;

    public ValKeyReconciliationLockAdapter(
            StringRedisTemplate redisTemplate,
            ValkeyCircuitBreaker circuitBreaker,
            @Value("${adclick.click.reconciliation.runner.lock-ttl-seconds:300}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.circuitBreaker = circuitBreaker;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public boolean tryLock(String lockKey) {
        return circuitBreaker.execute("reconciliation.lock", () -> {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + lockKey, "1", ttl);
            return Boolean.TRUE.equals(acquired);
        }, () -> true);
    }

    @Override
    public void release(String lockKey) {
        circuitBreaker.execute("reconciliation.unlock", () -> {
            redisTemplate.delete(KEY_PREFIX + lockKey);
            return null;
        }, () -> null);
    }
}
