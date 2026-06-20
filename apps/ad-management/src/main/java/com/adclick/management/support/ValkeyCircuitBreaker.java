package com.adclick.management.support;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.core.IntervalFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component("managementValkeyCircuitBreaker")
public class ValkeyCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(ValkeyCircuitBreaker.class);

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ValkeyCircuitBreaker(
            @Value("${adclick.valkey.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${adclick.valkey.circuit-breaker.sliding-window-size:5}") int slidingWindowSize,
            @Value("${adclick.valkey.circuit-breaker.minimum-number-of-calls:5}") int minimumNumberOfCalls,
            @Value("${adclick.valkey.circuit-breaker.wait-duration-in-open-state-ms:10000}") long waitDurationInOpenStateMs,
            @Value("${adclick.valkey.circuit-breaker.permitted-half-open-calls:2}") int permittedHalfOpenCalls,
            @Value("${adclick.valkey.retry.max-attempts:2}") int retryMaxAttempts,
            @Value("${adclick.valkey.retry.initial-interval-ms:50}") long retryInitialIntervalMs,
            @Value("${adclick.valkey.retry.multiplier:2.0}") double retryMultiplier,
            @Value("${adclick.valkey.retry.max-interval-ms:200}") long retryMaxIntervalMs) {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenStateMs))
                .permittedNumberOfCallsInHalfOpenState(permittedHalfOpenCalls)
                .build();
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(retryMaxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        Duration.ofMillis(retryInitialIntervalMs),
                        retryMultiplier,
                        0.0,
                        Duration.ofMillis(retryMaxIntervalMs)))
                .build();
        this.circuitBreaker = CircuitBreaker.of("valkey", circuitBreakerConfig);
        this.retry = Retry.of("valkey", retryConfig);
    }

    public <T> T execute(String operation, Supplier<T> action, Supplier<T> fallback) {
        try {
            Supplier<T> retriedAction = Retry.decorateSupplier(retry, action);
            Supplier<T> protectedAction = CircuitBreaker.decorateSupplier(circuitBreaker, retriedAction);
            return protectedAction.get();
        } catch (CallNotPermittedException e) {
            log.warn("valkey circuit breaker is open. operation={}", operation);
            return fallback.get();
        } catch (RuntimeException e) {
            log.warn("valkey operation failed. operation={}", operation, e);
            return fallback.get();
        }
    }

    public void execute(String operation, Runnable action) {
        execute(operation, () -> {
            action.run();
            return null;
        }, () -> null);
    }

    CircuitBreaker.State state() {
        return circuitBreaker.getState();
    }
}
