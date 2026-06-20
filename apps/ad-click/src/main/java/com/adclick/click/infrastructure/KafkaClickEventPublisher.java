package com.adclick.click.infrastructure;

import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventPublisher;
import com.adclick.click.infrastructure.message.ClickEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaClickEventPublisher implements ClickEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaClickEventPublisher.class);

    private final KafkaTemplate<String, ClickEventMessage> kafkaTemplate;
    private final String topic;

    public KafkaClickEventPublisher(
            KafkaTemplate<String, ClickEventMessage> kafkaTemplate,
            @Value("${adclick.kafka.topics.click-events:ad-click-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(ClickEvent event) {
        try {
            kafkaTemplate.send(topic, event.getAdId().toString(), ClickEventMessage.from(event))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("click event kafka publish failed. clickEventId={}", event.getId(), ex);
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("click event kafka publish skipped. clickEventId={}", event.getId(), e);
        }
    }
}
