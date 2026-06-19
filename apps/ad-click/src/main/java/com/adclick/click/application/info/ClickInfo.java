package com.adclick.click.application.info;

import com.adclick.click.domain.ClickEvent;
import java.time.LocalDateTime;

public record ClickInfo(Long id, Long adId, boolean isValid, LocalDateTime clickedAt) {

    public static ClickInfo from(ClickEvent event) {
        return new ClickInfo(event.getId(), event.getAdId(), event.isValid(), event.getClickedAt());
    }
}
