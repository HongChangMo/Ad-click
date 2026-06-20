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
import org.springframework.transaction.annotation.Transactional;

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

    public ClickEventOutboxRelay(
            ClickEventOutboxJpaRepository outboxRepository,
            KafkaClickEventPublisher kafkaClickEventPublisher,
            ObjectMapper objectMapper,
            @Value("${adclick.kafka.outbox.relay.publish-timeout-ms:1000}") long publishTimeoutMs) {
        this.outboxRepository = outboxRepository;
        this.kafkaClickEventPublisher = kafkaClickEventPublisher;
        this.objectMapper = objectMapper;
        this.publishTimeoutMs = publishTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${adclick.kafka.outbox.relay.fixed-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        List<ClickEventOutbox> events = outboxRepository
                .findTop50ByStatusOrderByCreatedAtAscIdAsc(ClickEventOutboxStatus.PENDING);

        for (ClickEventOutbox event : events) {
            publish(event);
        }
    }

    private void publish(ClickEventOutbox event) {
        try {
            ClickEventMessage message = objectMapper.readValue(event.getPayload(), ClickEventMessage.class);
            kafkaClickEventPublisher.publish(event.getTopic(), event.getMessageKey(), message)
                    .get(publishTimeoutMs, TimeUnit.MILLISECONDS);
            event.markPublished();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            event.markFailed(e.getMessage());
            log.warn("click event outbox publish interrupted. outboxId={}", event.getId(), e);
        } catch (Exception e) {
            event.markFailed(e.getMessage());
            log.warn("click event outbox publish failed. outboxId={}", event.getId(), e);
        }
    }
}
