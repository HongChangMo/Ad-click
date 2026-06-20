package com.adclick.aggregation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_click_events")
public class ProcessedClickEvent {

    @Id
    private Long clickEventId;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedClickEvent() {
    }

    public static ProcessedClickEvent of(Long clickEventId) {
        ProcessedClickEvent event = new ProcessedClickEvent();
        event.clickEventId = clickEventId;
        event.processedAt = LocalDateTime.now();
        return event;
    }

    public Long getClickEventId() {
        return clickEventId;
    }
}
