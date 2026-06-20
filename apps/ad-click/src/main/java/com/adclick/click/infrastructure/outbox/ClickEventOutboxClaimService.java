package com.adclick.click.infrastructure.outbox;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClickEventOutboxClaimService {

    private final ClickEventOutboxJpaRepository outboxRepository;

    public ClickEventOutboxClaimService(ClickEventOutboxJpaRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public List<ClickEventOutbox> claimPending(String relayId, int batchSize) {
        List<ClickEventOutbox> events = outboxRepository.findRetryablePendingForUpdate(
                ClickEventOutboxStatus.PENDING,
                LocalDateTime.now(),
                PageRequest.of(0, batchSize));
        events.forEach(event -> event.markProcessing(relayId));
        return List.copyOf(events);
    }

    @Transactional
    public int recoverStaleProcessing(Duration processingTimeout, int batchSize) {
        LocalDateTime staleBefore = LocalDateTime.now().minus(processingTimeout);
        List<ClickEventOutbox> staleEvents = outboxRepository.findStaleProcessingForUpdate(
                ClickEventOutboxStatus.PROCESSING,
                staleBefore,
                PageRequest.of(0, batchSize));
        LocalDateTime retryAt = LocalDateTime.now();
        staleEvents.forEach(event -> event.markPendingForRetry(retryAt));
        return staleEvents.size();
    }

    @Transactional
    public void markPublished(ClickEventOutbox event) {
        outboxRepository.findById(event.getId()).ifPresent(ClickEventOutbox::markPublished);
    }

    @Transactional
    public void markFailed(ClickEventOutbox event, String errorMessage, Duration retryDelay, int maxAttempts) {
        LocalDateTime retryAt = LocalDateTime.now().plus(retryDelay);
        outboxRepository.findById(event.getId())
                .ifPresent(found -> found.markFailed(errorMessage, retryAt, maxAttempts));
    }
}
