package com.adclick.click.infrastructure;

import com.adclick.click.domain.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ClickEventJpaRepository extends JpaRepository<ClickEvent, Long> {

    long countByAdIdAndIsValidAndClickedAtBetween(
            Long adId,
            boolean isValid,
            LocalDateTime from,
            LocalDateTime to);
}
