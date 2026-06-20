package com.adclick.click.infrastructure.outbox;

import com.adclick.click.infrastructure.KafkaClickEventPublisher;
import com.adclick.click.infrastructure.message.ClickEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(
        prefix = "adclick.kafka.outbox.relay",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ClickEventOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(ClickEventOutboxRelay.class);

    private final ClickEventOutboxClaimService claimService;
    private final KafkaClickEventPublisher kafkaClickEventPublisher;
    private final ObjectMapper objectMapper;
    private final long publishTimeoutMs;
    private final int batchSize;
    private final Duration retryInitialInterval;
    private final double retryMultiplier;
    private final Duration retryMaxInterval;
    private final Duration processingTimeout;
    private final int maxAttempts;
    private final String relayId;

    public ClickEventOutboxRelay(
            ClickEventOutboxClaimService claimService,
            KafkaClickEventPublisher kafkaClickEventPublisher,
            ObjectMapper objectMapper,
            @Value("${adclick.kafka.outbox.relay.publish-timeout-ms:1000}") long publishTimeoutMs,
            @Value("${adclick.kafka.outbox.relay.batch-size:100}") int batchSize,
            @Value("${adclick.kafka.outbox.relay.retry.initial-interval-ms:1000}") long retryInitialIntervalMs,
            @Value("${adclick.kafka.outbox.relay.retry.multiplier:2.0}") double retryMultiplier,
            @Value("${adclick.kafka.outbox.relay.retry.max-interval-ms:60000}") long retryMaxIntervalMs,
            @Value("${adclick.kafka.outbox.relay.retry.max-attempts:10}") int maxAttempts,
            @Value("${adclick.kafka.outbox.relay.processing-timeout-seconds:300}") long processingTimeoutSeconds,
            @Value("${adclick.kafka.outbox.relay.id:}") String configuredRelayId) {
        this.claimService = claimService;
        this.kafkaClickEventPublisher = kafkaClickEventPublisher;
        this.objectMapper = objectMapper;
        this.publishTimeoutMs = publishTimeoutMs;
        this.batchSize = batchSize;
        this.retryInitialInterval = Duration.ofMillis(retryInitialIntervalMs);
        this.retryMultiplier = retryMultiplier;
        this.retryMaxInterval = Duration.ofMillis(retryMaxIntervalMs);
        this.maxAttempts = maxAttempts;
        this.processingTimeout = Duration.ofSeconds(processingTimeoutSeconds);
        this.relayId = configuredRelayId == null || configuredRelayId.isBlank()
                ? defaultRelayId()
                : configuredRelayId;
    }

    @Scheduled(fixedDelayString = "${adclick.kafka.outbox.relay.fixed-delay-ms:1000}")
    public void publishPending() {
        int recoveredCount = claimService.recoverStaleProcessing(processingTimeout, batchSize);
        if (recoveredCount > 0) {
            log.info("stale click event outbox rows recovered. count={}", recoveredCount);
        }

        List<ClickEventOutbox> events = claimService.claimPending(relayId, batchSize);

        if (events.isEmpty()) {
            return;
        }

        List<PublishAttempt> attempts = new ArrayList<>();
        for (ClickEventOutbox event : events) {
            attempts.add(send(event));
        }
        for (PublishAttempt attempt : attempts) {
            complete(attempt);
        }
    }

    private PublishAttempt send(ClickEventOutbox event) {
        try {
            ClickEventMessage message = objectMapper.readValue(event.getPayload(), ClickEventMessage.class);
            return PublishAttempt.sent(
                    event,
                    kafkaClickEventPublisher.publish(event.getTopic(), event.getMessageKey(), message));
        } catch (Exception e) {
            claimService.markFailed(event, e.getMessage(), retryDelay(event.getAttemptCount()), maxAttempts);
            log.warn("click event outbox publish failed. outboxId={}", event.getId(), e);
        }
        return PublishAttempt.failed(event);
    }

    private void complete(PublishAttempt attempt) {
        if (attempt.future == null) {
            return;
        }
        try {
            attempt.future.get(publishTimeoutMs, TimeUnit.MILLISECONDS);
            claimService.markPublished(attempt.event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            claimService.markFailed(attempt.event, e.getMessage(), retryDelay(attempt.event.getAttemptCount()), maxAttempts);
            log.warn("click event outbox publish interrupted. outboxId={}", attempt.event.getId(), e);
        } catch (Exception e) {
            claimService.markFailed(attempt.event, e.getMessage(), retryDelay(attempt.event.getAttemptCount()), maxAttempts);
            log.warn("click event outbox publish failed. outboxId={}", attempt.event.getId(), e);
        }
    }

    private Duration retryDelay(int attemptCount) {
        double multiplier = Math.pow(retryMultiplier, Math.max(0, attemptCount));
        long delayMs = Math.round(retryInitialInterval.toMillis() * multiplier);
        return Duration.ofMillis(Math.min(delayMs, retryMaxInterval.toMillis()));
    }

    private String defaultRelayId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-relay";
        }
    }

    private record PublishAttempt(
            ClickEventOutbox event,
            java.util.concurrent.CompletableFuture<?> future) {

        static PublishAttempt sent(ClickEventOutbox event, java.util.concurrent.CompletableFuture<?> future) {
            return new PublishAttempt(event, future);
        }

        static PublishAttempt failed(ClickEventOutbox event) {
            return new PublishAttempt(event, null);
        }
    }
}
