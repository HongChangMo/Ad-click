package com.adclick.click.infrastructure;

import com.adclick.click.infrastructure.message.ClickEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaClickEventPublisherTest {

    @Test
    void publish_sends_click_event_message_to_configured_topic() {
        KafkaTemplate<String, ClickEventMessage> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaClickEventPublisher publisher = new KafkaClickEventPublisher(kafkaTemplate, "ad-click-events");
        ClickEventMessage message = new ClickEventMessage(
                100L,
                1L,
                "1.2.3.4",
                "anon-id",
                true,
                null,
                LocalDateTime.of(2026, 6, 20, 12, 0));

        publisher.publish(message);

        verify(kafkaTemplate).send(
                eq("ad-click-events"),
                eq("1"),
                eq(message));
    }
}
