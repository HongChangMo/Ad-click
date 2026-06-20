package com.adclick.click.interfaces.api;

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
    private final int maxRequests;
    private final Duration window;

    public ClickRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${adclick.click.rate-limit.max-requests:100}") int maxRequests,
            @Value("${adclick.click.rate-limit.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public boolean allow(String ipAddress) {
        try {
            String key = "rate:click:ip:" + ipAddress;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, window);
            }
            return count == null || count <= maxRequests;
        } catch (RuntimeException e) {
            log.warn("click rate limiter skipped due to valkey failure. ip={}", ipAddress, e);
            return true;
        }
    }
}
