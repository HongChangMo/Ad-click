package com.adclick.click.infrastructure.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ClickEventOutboxAdminServiceTest {

    @Test
    void findFailed_caps_page_size_to_100() {
        ClickEventOutboxJpaRepository repository = mock(ClickEventOutboxJpaRepository.class);
        ClickEventOutboxAdminService service = new ClickEventOutboxAdminService(repository);
        ClickEventOutbox event = failedEvent();
        given(repository.findByStatusOrderByFailedAtDescIdDesc(
                ClickEventOutboxStatus.FAILED,
                PageRequest.of(0, 100)))
                .willReturn(List.of(event));

        List<ClickEventOutbox> failed = service.findFailed(1000);

        assertThat(failed).containsExactly(event);
        verify(repository).findByStatusOrderByFailedAtDescIdDesc(
                ClickEventOutboxStatus.FAILED,
                PageRequest.of(0, 100));
    }

    @Test
    void retryFailed_returns_failed_row_to_pending() {
        ClickEventOutboxJpaRepository repository = mock(ClickEventOutboxJpaRepository.class);
        ClickEventOutboxAdminService service = new ClickEventOutboxAdminService(repository);
        ClickEventOutbox event = failedEvent();
        given(repository.findByIdAndStatus(100L, ClickEventOutboxStatus.FAILED))
                .willReturn(Optional.of(event));

        Optional<ClickEventOutbox> retried = service.retryFailed(100L);

        assertThat(retried).contains(event);
        assertThat(event.getStatus()).isEqualTo(ClickEventOutboxStatus.PENDING);
        assertThat(event.getClaimedBy()).isNull();
        assertThat(event.getClaimedAt()).isNull();
        assertThat(event.getFailedAt()).isNull();
        assertThat(event.getNextRetryAt()).isAfterOrEqualTo(LocalDateTime.now().minusSeconds(1));
    }

    @Test
    void retryFailed_returns_empty_when_failed_row_does_not_exist() {
        ClickEventOutboxJpaRepository repository = mock(ClickEventOutboxJpaRepository.class);
        ClickEventOutboxAdminService service = new ClickEventOutboxAdminService(repository);
        given(repository.findByIdAndStatus(100L, ClickEventOutboxStatus.FAILED))
                .willReturn(Optional.empty());

        Optional<ClickEventOutbox> retried = service.retryFailed(100L);

        assertThat(retried).isEmpty();
    }

    private ClickEventOutbox failedEvent() {
        ClickEventOutbox event = ClickEventOutbox.pending("ad-click-events", "1", "{}");
        event.markProcessing("relay-1");
        event.markFailed("broker unavailable", LocalDateTime.now().plus(Duration.ofSeconds(5)), 1);
        return event;
    }
}
