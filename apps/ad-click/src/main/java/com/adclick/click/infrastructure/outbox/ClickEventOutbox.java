package com.adclick.click.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "click_event_outbox",
        indexes = {
                @jakarta.persistence.Index(
                        name = "idx_click_event_outbox_status_retry_created",
                        columnList = "status, next_retry_at, created_at, id"),
                @jakarta.persistence.Index(
                        name = "idx_click_event_outbox_processing_claimed",
                        columnList = "status, claimed_at, id")
        })
public class ClickEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClickEventOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "claimed_by", length = 100)
    private String claimedBy;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    protected ClickEventOutbox() {
    }

    private ClickEventOutbox(String topic, String messageKey, String payload) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.status = ClickEventOutboxStatus.PENDING;
        this.attemptCount = 0;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.nextRetryAt = now;
    }

    public static ClickEventOutbox pending(String topic, String messageKey, String payload) {
        return new ClickEventOutbox(topic, messageKey, payload);
    }

    public void markProcessing(String claimedBy) {
        this.status = ClickEventOutboxStatus.PROCESSING;
        this.claimedBy = claimedBy;
        this.claimedAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.status = ClickEventOutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.failedAt = null;
        this.lastError = null;
        this.claimedBy = null;
        this.claimedAt = null;
    }

    public void markFailed(String errorMessage, LocalDateTime nextRetryAt, int maxAttempts) {
        this.attemptCount++;
        this.lastError = truncate(errorMessage);
        this.claimedBy = null;
        this.claimedAt = null;
        if (this.attemptCount >= maxAttempts) {
            this.status = ClickEventOutboxStatus.FAILED;
            this.failedAt = LocalDateTime.now();
            return;
        }
        this.status = ClickEventOutboxStatus.PENDING;
        this.nextRetryAt = nextRetryAt;
    }

    public void markPendingForRetry(LocalDateTime nextRetryAt) {
        this.status = ClickEventOutboxStatus.PENDING;
        this.claimedBy = null;
        this.claimedAt = null;
        this.nextRetryAt = nextRetryAt;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }

    public Long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public ClickEventOutboxStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }
}
