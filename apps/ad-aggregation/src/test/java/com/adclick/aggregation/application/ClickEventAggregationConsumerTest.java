package com.adclick.aggregation.application;

import com.adclick.aggregation.message.ClickEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickEventAggregationConsumerTest {

    @Test
    void consume_aggregates_message_and_acknowledges_offset() {
        ClickAggregationService aggregationService = mock(ClickAggregationService.class);
        ClickEventAggregationConsumer consumer = new ClickEventAggregationConsumer(aggregationService);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ClickEventMessage message = new ClickEventMessage(
                1L,
                10L,
                "1.2.3.4",
                "anon-id",
                true,
                null,
                LocalDateTime.of(2026, 6, 20, 12, 0));
        when(aggregationService.aggregate(message)).thenReturn(true);

        consumer.consume(message, acknowledgment);

        verify(aggregationService).aggregate(message);
        verify(acknowledgment).acknowledge();
    }
}
