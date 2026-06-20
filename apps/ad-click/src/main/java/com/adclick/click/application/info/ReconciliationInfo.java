package com.adclick.click.application.info;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReconciliationInfo(
        LocalDateTime from,
        LocalDateTime to,
        long invalidatedCount,
        BigDecimal refundedAmount
) {
}
