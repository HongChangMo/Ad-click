package com.adclick.management.infrastructure;

import com.adclick.management.domain.AdRotationQueuePort;
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

    public ValKeyRotationAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void offer(Long adId) {
        redisTemplate.opsForList().rightPush(QUEUE_KEY, adId.toString());
    }

    @Override
    public void remove(Long adId) {
        redisTemplate.opsForList().remove(QUEUE_KEY, 0, adId.toString());
    }

    @Override
    public Optional<Long> poll() {
        String value = redisTemplate.opsForList().leftPop(QUEUE_KEY);
        return Optional.ofNullable(value).map(Long::parseLong);
    }

    @Override
    public boolean tryRebuildLock() {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void releaseRebuildLock() {
        redisTemplate.delete(LOCK_KEY);
    }
}
