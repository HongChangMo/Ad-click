package com.adclick.click.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface ClickEventRepository {
    ClickEvent save(ClickEvent event);

    List<ClickEvent> saveAll(List<ClickEvent> events);

    List<ClickEvent> findValidEventsBetween(LocalDateTime from, LocalDateTime to);

    long countByAdIdAndValidityBetween(Long adId, boolean isValid, LocalDateTime from, LocalDateTime to);
}
