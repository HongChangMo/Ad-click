package com.adclick.aggregation.application;

import com.adclick.aggregation.message.ClickEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

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
    public void consume(ClickEventMessage message, Acknowledgment acknowledgment) {
        boolean processed = aggregationService.aggregate(message);
        log.info(
                "click event consumed for aggregation. clickEventId={}, adId={}, valid={}, processed={}",
                message.clickEventId(),
                message.adId(),
                message.valid(),
                processed);
        acknowledgment.acknowledge();
    }
}
