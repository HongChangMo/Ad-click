package com.adclick.click.infrastructure.outbox;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ClickEventOutboxAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ClickEventOutboxJpaRepository outboxRepository;

    public ClickEventOutboxAdminService(ClickEventOutboxJpaRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional(readOnly = true)
    public List<ClickEventOutbox> findFailed(int size) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return outboxRepository.findByStatusOrderByFailedAtDescIdDesc(
                ClickEventOutboxStatus.FAILED,
                PageRequest.of(0, pageSize));
    }

    @Transactional
    public Optional<ClickEventOutbox> retryFailed(Long outboxId) {
        Optional<ClickEventOutbox> event = outboxRepository.findByIdAndStatus(
                outboxId,
                ClickEventOutboxStatus.FAILED);
        event.ifPresent(found -> found.markPendingForManualRetry(LocalDateTime.now()));
        return event;
    }
}
