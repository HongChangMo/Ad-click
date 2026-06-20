package com.adclick.click.infrastructure;

import com.adclick.click.infrastructure.message.ClickEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class KafkaClickEventPublisher {

    private final KafkaTemplate<String, ClickEventMessage> kafkaTemplate;
    private final String topic;

    public KafkaClickEventPublisher(
            KafkaTemplate<String, ClickEventMessage> kafkaTemplate,
            @Value("${adclick.kafka.topics.click-events:ad-click-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public CompletableFuture<SendResult<String, ClickEventMessage>> publish(ClickEventMessage message) {
        return publish(topic, message.adId().toString(), message);
    }

    public CompletableFuture<SendResult<String, ClickEventMessage>> publish(
            String topic,
            String messageKey,
            ClickEventMessage message) {
        return kafkaTemplate.send(topic, messageKey, message);
    }
}
