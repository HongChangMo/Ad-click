package com.adclick.aggregation.application;

import com.adclick.aggregation.message.ClickEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClickEventAggregationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClickEventAggregationConsumer.class);

    private final ClickAggregationService aggregationService;

    public ClickEventAggregationConsumer(ClickAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @KafkaListener(
            topics = "${adclick.kafka.topics.click-events:ad-click-events}",
            groupId = "${spring.kafka.consumer.group-id:ad-click-aggregation}")
    public void consume(List<ClickEventMessage> messages, Acknowledgment acknowledgment) {
        int processedCount = aggregationService.aggregateAll(messages);
        log.info(
                "click event batch consumed for aggregation. receivedCount={}, processedCount={}",
                messages.size(),
                processedCount);
        acknowledgment.acknowledge();
    }
}
