package com.adclick.click.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "click_events",
    indexes = {
        @Index(name = "idx_click_abuse", columnList = "ad_id, ip_address, clicked_at"),
        @Index(name = "idx_click_stats", columnList = "ad_id, clicked_at, is_valid")
    })
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad_id", nullable = false)
    private Long adId;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "anonymous_id", length = 64)
    private String anonymousId;

    @Column(name = "clicked_at", nullable = false)
    private LocalDateTime clickedAt;

    @Column(name = "is_valid", nullable = false)
    private boolean isValid;

    @Column(name = "invalid_reason", length = 30)
    private String invalidReason;

    protected ClickEvent() {}

    public static ClickEvent valid(Long adId, String ipAddress, String anonymousId) {
        ClickEvent e = new ClickEvent();
        e.adId = adId;
        e.ipAddress = ipAddress;
        e.anonymousId = anonymousId;
        e.clickedAt = LocalDateTime.now();
        e.isValid = true;
        return e;
    }

    public Long getId() { return id; }
    public Long getAdId() { return adId; }
    public String getIpAddress() { return ipAddress; }
    public String getAnonymousId() { return anonymousId; }
    public LocalDateTime getClickedAt() { return clickedAt; }
    public boolean isValid() { return isValid; }
    public String getInvalidReason() { return invalidReason; }
}