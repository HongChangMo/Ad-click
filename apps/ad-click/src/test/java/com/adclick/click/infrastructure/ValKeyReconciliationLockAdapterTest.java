package com.adclick.click.infrastructure;

import com.adclick.click.support.ValkeyCircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ValKeyReconciliationLockAdapterTest {

    @Test
    void tryLock_returns_true_when_setIfAbsent_succeeds() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent("reconciliation:lock:scheduled", "1", Duration.ofSeconds(300)))
                .thenReturn(true);
        ValKeyReconciliationLockAdapter adapter = new ValKeyReconciliationLockAdapter(
                redisTemplate,
                circuitBreaker(),
                300);

        assertThat(adapter.tryLock("scheduled")).isTrue();
    }

    @Test
    void tryLock_returns_false_when_lock_already_exists() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent("reconciliation:lock:scheduled", "1", Duration.ofSeconds(300)))
                .thenReturn(false);
        ValKeyReconciliationLockAdapter adapter = new ValKeyReconciliationLockAdapter(
                redisTemplate,
                circuitBreaker(),
                300);

        assertThat(adapter.tryLock("scheduled")).isFalse();
    }

    @Test
    void tryLock_fails_open_when_valkey_fails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("connection refused"));
        ValKeyReconciliationLockAdapter adapter = new ValKeyReconciliationLockAdapter(
                redisTemplate,
                circuitBreaker(),
                300);

        assertThat(adapter.tryLock("scheduled")).isTrue();
    }

    @Test
    void release_deletes_lock_key() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValKeyReconciliationLockAdapter adapter = new ValKeyReconciliationLockAdapter(
                redisTemplate,
                circuitBreaker(),
                300);

        adapter.release("scheduled");

        verify(redisTemplate).delete("reconciliation:lock:scheduled");
    }

    private ValkeyCircuitBreaker circuitBreaker() {
        return new ValkeyCircuitBreaker(50, 2, 2, 10_000, 1, 1, 1, 2.0, 5);
    }
}
