package com.adclick.click.infrastructure.outbox;

import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventPublisher;
import com.adclick.click.infrastructure.message.ClickEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OutboxClickEventPublisher implements ClickEventPublisher {

    private final ClickEventOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String topic;

    public OutboxClickEventPublisher(
            ClickEventOutboxJpaRepository outboxRepository,
            ObjectMapper objectMapper,
            @Value("${adclick.kafka.topics.click-events:ad-click-events}") String topic) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(ClickEvent event) {
        ClickEventMessage message = ClickEventMessage.from(event);
        String payload = serialize(message);
        outboxRepository.save(ClickEventOutbox.pending(topic, event.getAdId().toString(), payload));
    }

    private String serialize(ClickEventMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("click event outbox serialization failed", e);
        }
    }
}
