package com.adclick.click.infrastructure.outbox;

import com.adclick.click.infrastructure.KafkaClickEventPublisher;
import com.adclick.click.infrastructure.message.ClickEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    private final ClickEventOutboxJpaRepository outboxRepository;
    private final KafkaClickEventPublisher kafkaClickEventPublisher;
    private final ObjectMapper objectMapper;
    private final long publishTimeoutMs;
    private final int batchSize;

    public ClickEventOutboxRelay(
            ClickEventOutboxJpaRepository outboxRepository,
            KafkaClickEventPublisher kafkaClickEventPublisher,
            ObjectMapper objectMapper,
            @Value("${adclick.kafka.outbox.relay.publish-timeout-ms:1000}") long publishTimeoutMs,
            @Value("${adclick.kafka.outbox.relay.batch-size:100}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.kafkaClickEventPublisher = kafkaClickEventPublisher;
        this.objectMapper = objectMapper;
        this.publishTimeoutMs = publishTimeoutMs;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${adclick.kafka.outbox.relay.fixed-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        List<ClickEventOutbox> events = outboxRepository
                .findByStatusOrderByCreatedAtAscIdAsc(
                        ClickEventOutboxStatus.PENDING,
                        PageRequest.of(0, batchSize));

        if (events.isEmpty()) {
            return;
        }

        events.forEach(ClickEventOutbox::markProcessing);
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
            event.markFailed(e.getMessage());
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
            attempt.event.markPublished();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            attempt.event.markFailed(e.getMessage());
            log.warn("click event outbox publish interrupted. outboxId={}", attempt.event.getId(), e);
        } catch (Exception e) {
            attempt.event.markFailed(e.getMessage());
            log.warn("click event outbox publish failed. outboxId={}", attempt.event.getId(), e);
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
