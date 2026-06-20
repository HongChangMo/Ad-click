package com.adclick.click.infrastructure.message;

import com.adclick.click.domain.ClickEvent;

import java.time.LocalDateTime;

public record ClickEventMessage(
        Long clickEventId,
        Long adId,
        String ipAddress,
        String anonymousId,
        boolean valid,
        String invalidReason,
        LocalDateTime clickedAt
) {

    public static ClickEventMessage from(ClickEvent event) {
        return new ClickEventMessage(
                event.getId(),
                event.getAdId(),
                event.getIpAddress(),
                event.getAnonymousId(),
                event.isValid(),
                event.getInvalidReason(),
                event.getClickedAt());
    }
}
