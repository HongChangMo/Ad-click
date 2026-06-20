package com.adclick.click.application.info;

import java.time.LocalDateTime;

public record ClickStatsInfo(
        Long adId,
        LocalDateTime from,
        LocalDateTime to,
        long validCount,
        long invalidCount
) {
}
