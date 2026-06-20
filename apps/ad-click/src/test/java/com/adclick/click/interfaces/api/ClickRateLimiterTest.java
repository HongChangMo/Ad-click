package com.adclick.click.interfaces.api;

import com.adclick.click.support.ValkeyCircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ClickRateLimiterTest {

    @Test
    void allow_fails_open_and_stops_calling_redis_when_circuit_opens() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("connection refused"));
        ClickRateLimiter rateLimiter = new ClickRateLimiter(
                redisTemplate,
                circuitBreaker(),
                100,
                60);

        assertThat(rateLimiter.allow("1.2.3.4")).isTrue();
        assertThat(rateLimiter.allow("1.2.3.4")).isTrue();
        assertThat(rateLimiter.allow("1.2.3.4")).isTrue();

        verify(redisTemplate, times(4)).opsForValue();
    }

    private ValkeyCircuitBreaker circuitBreaker() {
        return new ValkeyCircuitBreaker(50, 2, 2, 10_000, 1, 2, 1, 2.0, 5);
    }
}
