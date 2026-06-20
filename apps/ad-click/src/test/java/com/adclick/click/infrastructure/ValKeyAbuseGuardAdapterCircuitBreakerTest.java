package com.adclick.click.infrastructure;

import com.adclick.click.support.ValkeyCircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ValKeyAbuseGuardAdapterCircuitBreakerTest {

    @Test
    void checkAndMark_fails_open_and_stops_calling_redis_when_circuit_opens() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("connection refused"));
        ValKeyAbuseGuardAdapter adapter = new ValKeyAbuseGuardAdapter(
                redisTemplate,
                circuitBreaker(),
                60);

        assertThat(adapter.checkAndMark(1L, "1.2.3.4", "anon-id")).isEqualTo(Optional.empty());
        assertThat(adapter.checkAndMark(1L, "1.2.3.4", "anon-id")).isEqualTo(Optional.empty());
        assertThat(adapter.checkAndMark(1L, "1.2.3.4", "anon-id")).isEqualTo(Optional.empty());

        verify(redisTemplate, times(4)).hasKey("abuse:ip:1.2.3.4:1");
    }

    private ValkeyCircuitBreaker circuitBreaker() {
        return new ValkeyCircuitBreaker(50, 2, 2, 10_000, 1, 2, 1, 2.0, 5);
    }
}
