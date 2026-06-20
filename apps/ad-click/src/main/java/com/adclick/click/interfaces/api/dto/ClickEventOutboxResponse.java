package com.adclick.click.interfaces.api.dto;

import com.adclick.click.infrastructure.outbox.ClickEventOutbox;
import com.adclick.click.infrastructure.outbox.ClickEventOutboxStatus;

import java.time.LocalDateTime;

public record ClickEventOutboxResponse(
        Long id,
        String topic,
        String messageKey,
        ClickEventOutboxStatus status,
        int attemptCount,
        String lastError,
        LocalDateTime nextRetryAt,
        LocalDateTime createdAt,
        LocalDateTime publishedAt,
        LocalDateTime failedAt) {

    public static ClickEventOutboxResponse from(ClickEventOutbox event) {
        return new ClickEventOutboxResponse(
                event.getId(),
                event.getTopic(),
                event.getMessageKey(),
                event.getStatus(),
                event.getAttemptCount(),
                event.getLastError(),
                event.getNextRetryAt(),
                event.getCreatedAt(),
                event.getPublishedAt(),
                event.getFailedAt());
    }
}
