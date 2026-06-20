package com.adclick.click.infrastructure.outbox;

import com.adclick.click.infrastructure.KafkaClickEventPublisher;
import com.adclick.click.infrastructure.message.ClickEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ClickEventOutboxRelayTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publishPending_sends_pending_outbox_event_and_marks_published() throws Exception {
        ClickEventOutboxJpaRepository outboxRepository = mock(ClickEventOutboxJpaRepository.class);
        KafkaClickEventPublisher kafkaPublisher = mock(KafkaClickEventPublisher.class);
        ClickEventOutboxRelay relay = new ClickEventOutboxRelay(outboxRepository, kafkaPublisher, objectMapper, 1000);
        ClickEventMessage message = message();
        ClickEventOutbox outbox = ClickEventOutbox.pending(
                "ad-click-events",
                "1",
                objectMapper.writeValueAsString(message));
        given(outboxRepository.findTop50ByStatusOrderByCreatedAtAscIdAsc(ClickEventOutboxStatus.PENDING))
                .willReturn(List.of(outbox));
        given(kafkaPublisher.publish(eq("ad-click-events"), eq("1"), any(ClickEventMessage.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        verify(kafkaPublisher).publish(eq("ad-click-events"), eq("1"), any(ClickEventMessage.class));
        assertThat(outbox.getStatus()).isEqualTo(ClickEventOutboxStatus.PUBLISHED);
        assertThat(outbox.getPublishedAt()).isNotNull();
        assertThat(outbox.getAttemptCount()).isZero();
    }

    @Test
    void publishPending_keeps_pending_and_records_failure_when_kafka_publish_fails() throws Exception {
        ClickEventOutboxJpaRepository outboxRepository = mock(ClickEventOutboxJpaRepository.class);
        KafkaClickEventPublisher kafkaPublisher = mock(KafkaClickEventPublisher.class);
        ClickEventOutboxRelay relay = new ClickEventOutboxRelay(outboxRepository, kafkaPublisher, objectMapper, 1000);
        ClickEventOutbox outbox = ClickEventOutbox.pending(
                "ad-click-events",
                "1",
                objectMapper.writeValueAsString(message()));
        CompletableFuture<SendResult<String, ClickEventMessage>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        given(outboxRepository.findTop50ByStatusOrderByCreatedAtAscIdAsc(ClickEventOutboxStatus.PENDING))
                .willReturn(List.of(outbox));
        given(kafkaPublisher.publish(eq("ad-click-events"), eq("1"), any(ClickEventMessage.class)))
                .willReturn(failed);

        relay.publishPending();

        assertThat(outbox.getStatus()).isEqualTo(ClickEventOutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getLastError()).contains("broker unavailable");
    }

    private ClickEventMessage message() {
        return new ClickEventMessage(
                100L,
                1L,
                "1.2.3.4",
                "anon-id",
                true,
                null,
                LocalDateTime.of(2026, 6, 20, 12, 0));
    }
}
