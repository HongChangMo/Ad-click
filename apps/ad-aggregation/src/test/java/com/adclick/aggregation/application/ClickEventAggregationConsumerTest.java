package com.adclick.aggregation.application;

import com.adclick.aggregation.message.ClickEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(aggregationService.aggregateAll(List.of(message))).thenReturn(1);

        consumer.consume(List.of(message), acknowledgment);

        verify(aggregationService).aggregateAll(List.of(message));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_does_not_acknowledge_when_batch_processing_fails() {
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
        when(aggregationService.aggregateAll(List.of(message)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> consumer.consume(List.of(message), acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(aggregationService).aggregateAll(List.of(message));
        verify(acknowledgment, never()).acknowledge();
    }
}
