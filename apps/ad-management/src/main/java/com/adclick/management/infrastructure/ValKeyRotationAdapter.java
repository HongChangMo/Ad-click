package com.adclick.management.infrastructure;

import com.adclick.management.domain.AdRotationQueuePort;
import com.adclick.management.support.ValkeyCircuitBreaker;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class ValKeyRotationAdapter implements AdRotationQueuePort {

    private static final String QUEUE_KEY = "ad:rotation:queue";
    private static final String LOCK_KEY = "ad:rotation:rebuild:lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;
    private final ValkeyCircuitBreaker circuitBreaker;

    public ValKeyRotationAdapter(StringRedisTemplate redisTemplate, ValkeyCircuitBreaker circuitBreaker) {
        this.redisTemplate = redisTemplate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void offer(Long adId) {
        circuitBreaker.execute("rotation.offer",
                () -> redisTemplate.opsForList().rightPush(QUEUE_KEY, adId.toString()));
    }

    @Override
    public void remove(Long adId) {
        circuitBreaker.execute("rotation.remove",
                () -> redisTemplate.opsForList().remove(QUEUE_KEY, 0, adId.toString()));
    }

    @Override
    public Optional<Long> poll() {
        return circuitBreaker.execute("rotation.poll", () -> {
            String value = redisTemplate.opsForList().leftPop(QUEUE_KEY);
            return Optional.ofNullable(value).map(Long::parseLong);
        }, Optional::empty);
    }

    @Override
    public boolean tryRebuildLock() {
        return circuitBreaker.execute("rotation.tryRebuildLock", () -> {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        }, () -> false);
    }

    @Override
    public void releaseRebuildLock() {
        circuitBreaker.execute("rotation.releaseRebuildLock",
                () -> redisTemplate.delete(LOCK_KEY));
    }
}
