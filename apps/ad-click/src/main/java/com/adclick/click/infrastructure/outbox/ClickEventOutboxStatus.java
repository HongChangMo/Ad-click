package com.adclick.click.infrastructure.outbox;

public enum ClickEventOutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}
