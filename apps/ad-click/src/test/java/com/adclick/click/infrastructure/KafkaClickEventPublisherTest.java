package com.adclick.click.infrastructure;

import com.adclick.click.domain.ClickEvent;
import com.adclick.click.infrastructure.message.ClickEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaClickEventPublisherTest {

    @Test
    void publish_sends_click_event_message_to_configured_topic() {
        KafkaTemplate<String, ClickEventMessage> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaClickEventPublisher publisher = new KafkaClickEventPublisher(kafkaTemplate, "ad-click-events");
        ClickEvent event = ClickEvent.valid(1L, "1.2.3.4", "anon-id");

        publisher.publish(event);

        verify(kafkaTemplate).send(
                eq("ad-click-events"),
                eq("1"),
                org.mockito.ArgumentMatchers.argThat(message ->
                        message.adId().equals(1L)
                                && message.ipAddress().equals("1.2.3.4")
                                && message.anonymousId().equals("anon-id")
                                && message.valid()));
    }
}
