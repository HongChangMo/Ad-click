package com.adclick.click.domain;

public interface ClickEventRepository {
    ClickEvent save(ClickEvent event);

    long countByAdIdAndValidityBetween(Long adId, boolean isValid, java.time.LocalDateTime from, java.time.LocalDateTime to);
}
