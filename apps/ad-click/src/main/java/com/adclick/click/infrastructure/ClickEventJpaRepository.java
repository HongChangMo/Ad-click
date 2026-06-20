package com.adclick.click.infrastructure;

import com.adclick.click.domain.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ClickEventJpaRepository extends JpaRepository<ClickEvent, Long> {

    List<ClickEvent> findByIsValidTrueAndClickedAtBetweenOrderByAdIdAscIpAddressAscClickedAtAscIdAsc(
            LocalDateTime from,
            LocalDateTime to);

    long countByAdIdAndIsValidAndClickedAtBetween(
            Long adId,
            boolean isValid,
            LocalDateTime from,
            LocalDateTime to);
}
