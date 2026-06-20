package com.adclick.management.infrastructure;

import com.adclick.management.support.ValkeyCircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ValKeyRotationAdapterCircuitBreakerTest {

    @Test
    void poll_returns_empty_and_stops_calling_redis_when_circuit_opens() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForList()).thenThrow(new RuntimeException("connection refused"));
        ValKeyRotationAdapter adapter = new ValKeyRotationAdapter(redisTemplate, circuitBreaker());

        assertThat(adapter.poll()).isEqualTo(Optional.empty());
        assertThat(adapter.poll()).isEqualTo(Optional.empty());
        assertThat(adapter.poll()).isEqualTo(Optional.empty());

        verify(redisTemplate, times(4)).opsForList();
    }

    @Test
    void tryRebuildLock_returns_false_on_valkey_failure() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("connection refused"));
        ValKeyRotationAdapter adapter = new ValKeyRotationAdapter(redisTemplate, circuitBreaker());

        assertThat(adapter.tryRebuildLock()).isFalse();
    }

    private ValkeyCircuitBreaker circuitBreaker() {
        return new ValkeyCircuitBreaker(50, 2, 2, 10_000, 1, 2, 1, 2.0, 5);
    }
}
