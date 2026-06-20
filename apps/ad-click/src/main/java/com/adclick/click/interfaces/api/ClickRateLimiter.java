package com.adclick.click.interfaces.api;

import com.adclick.click.support.ValkeyCircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ClickRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ClickRateLimiter.class);

    private final StringRedisTemplate redisTemplate;
    private final ValkeyCircuitBreaker circuitBreaker;
    private final int maxRequests;
    private final Duration window;

    public ClickRateLimiter(
            StringRedisTemplate redisTemplate,
            ValkeyCircuitBreaker circuitBreaker,
            @Value("${adclick.click.rate-limit.max-requests:100}") int maxRequests,
            @Value("${adclick.click.rate-limit.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.circuitBreaker = circuitBreaker;
        this.maxRequests = maxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public boolean allow(String ipAddress) {
        return circuitBreaker.execute("rateLimit.allow",
                () -> allowWithValkey(ipAddress),
                () -> {
                    log.warn("click rate limiter skipped because valkey is unavailable. ip={}", ipAddress);
                    return true;
                });
    }

    private boolean allowWithValkey(String ipAddress) {
        String key = "rate:click:ip:" + ipAddress;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count == null || count <= maxRequests;
    }
}
