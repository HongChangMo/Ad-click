package com.adclick.click.interfaces.api.dto;

import java.time.LocalDateTime;

public record ReconciliationRequest(LocalDateTime from, LocalDateTime to) {
}
