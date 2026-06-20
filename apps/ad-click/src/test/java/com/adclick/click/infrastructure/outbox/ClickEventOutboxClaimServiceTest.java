package com.adclick.click.infrastructure.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ClickEventOutboxClaimServiceTest {

    @Test
    void claimPending_marks_retryable_pending_rows_as_processing() {
        ClickEventOutboxJpaRepository repository = mock(ClickEventOutboxJpaRepository.class);
        ClickEventOutboxClaimService service = new ClickEventOutboxClaimService(repository);
        ClickEventOutbox event = ClickEventOutbox.pending("ad-click-events", "1", "{}");
        given(repository.findRetryablePendingForUpdate(
                eq(ClickEventOutboxStatus.PENDING),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 10))))
                .willReturn(List.of(event));

        List<ClickEventOutbox> claimed = service.claimPending("relay-1", 10);

        assertThat(claimed).containsExactly(event);
        assertThat(event.getStatus()).isEqualTo(ClickEventOutboxStatus.PROCESSING);
        assertThat(event.getClaimedBy()).isEqualTo("relay-1");
        assertThat(event.getClaimedAt()).isNotNull();
    }

    @Test
    void recoverStaleProcessing_returns_old_processing_rows_to_pending() {
        ClickEventOutboxJpaRepository repository = mock(ClickEventOutboxJpaRepository.class);
        ClickEventOutboxClaimService service = new ClickEventOutboxClaimService(repository);
        ClickEventOutbox event = ClickEventOutbox.pending("ad-click-events", "1", "{}");
        event.markProcessing("relay-1");
        given(repository.findStaleProcessingForUpdate(
                eq(ClickEventOutboxStatus.PROCESSING),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 10))))
                .willReturn(List.of(event));

        int recoveredCount = service.recoverStaleProcessing(Duration.ofMinutes(5), 10);

        assertThat(recoveredCount).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(ClickEventOutboxStatus.PENDING);
        assertThat(event.getClaimedBy()).isNull();
        assertThat(event.getClaimedAt()).isNull();
    }

    @Test
    void markFailed_sets_nextRetryAt_and_returns_row_to_pending() {
        ClickEventOutboxJpaRepository repository = mock(ClickEventOutboxJpaRepository.class);
        ClickEventOutboxClaimService service = new ClickEventOutboxClaimService(repository);
        ClickEventOutbox event = ClickEventOutbox.pending("ad-click-events", "1", "{}");
        event.markProcessing("relay-1");
        given(repository.findById(event.getId())).willReturn(Optional.of(event));

        service.markFailed(event, "broker unavailable", Duration.ofSeconds(5), 10);

        verify(repository).findById(event.getId());
        assertThat(event.getStatus()).isEqualTo(ClickEventOutboxStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("broker unavailable");
        assertThat(event.getNextRetryAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void markFailed_moves_row_to_failed_when_max_attempts_is_reached() {
        ClickEventOutboxJpaRepository repository = mock(ClickEventOutboxJpaRepository.class);
        ClickEventOutboxClaimService service = new ClickEventOutboxClaimService(repository);
        ClickEventOutbox event = ClickEventOutbox.pending("ad-click-events", "1", "{}");
        event.markProcessing("relay-1");
        given(repository.findById(event.getId())).willReturn(Optional.of(event));

        service.markFailed(event, "broker unavailable", Duration.ofSeconds(5), 1);

        assertThat(event.getStatus()).isEqualTo(ClickEventOutboxStatus.FAILED);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("broker unavailable");
        assertThat(event.getClaimedBy()).isNull();
        assertThat(event.getClaimedAt()).isNull();
        assertThat(event.getFailedAt()).isNotNull();
    }
}
