package com.adclick.aggregation.message;

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
}
