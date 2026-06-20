package com.adclick.aggregation.application;

import com.adclick.aggregation.message.ClickEventMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class ClickEventAggregationConsumerTest {

    @Test
    void consume_accepts_click_event_message() {
        ClickEventAggregationConsumer consumer = new ClickEventAggregationConsumer();
        ClickEventMessage message = new ClickEventMessage(
                1L,
                10L,
                "1.2.3.4",
                "anon-id",
                true,
                null,
                LocalDateTime.of(2026, 6, 20, 12, 0));

        consumer.consume(message);
    }
}
