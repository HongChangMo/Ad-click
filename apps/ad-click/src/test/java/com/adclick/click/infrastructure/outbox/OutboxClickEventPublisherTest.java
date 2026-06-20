package com.adclick.click.infrastructure.outbox;

import com.adclick.click.domain.ClickEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxClickEventPublisherTest {

    @Test
    void publish_saves_pending_outbox_event_instead_of_sending_kafka_directly() {
        ClickEventOutboxJpaRepository outboxRepository = mock(ClickEventOutboxJpaRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OutboxClickEventPublisher publisher =
                new OutboxClickEventPublisher(outboxRepository, objectMapper, "ad-click-events");
        ClickEvent event = ClickEvent.valid(1L, "1.2.3.4", "anon-id");

        publisher.publish(event);

        var captor = forClass(ClickEventOutbox.class);
        verify(outboxRepository).save(captor.capture());
        ClickEventOutbox outbox = captor.getValue();
        assertThat(outbox.getTopic()).isEqualTo("ad-click-events");
        assertThat(outbox.getMessageKey()).isEqualTo("1");
        assertThat(outbox.getStatus()).isEqualTo(ClickEventOutboxStatus.PENDING);
        assertThat(outbox.getPayload()).contains("\"adId\":1");
        assertThat(outbox.getPayload()).contains("\"ipAddress\":\"1.2.3.4\"");
    }
}
