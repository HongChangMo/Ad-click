package com.adclick.aggregation.infrastructure;

import com.adclick.aggregation.domain.ClickDailyStats;
import com.adclick.aggregation.domain.ClickDailyStatsId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ClickDailyStatsJpaRepository extends JpaRepository<ClickDailyStats, ClickDailyStatsId> {

    ClickDailyStats findByAdIdAndStatsDate(Long adId, LocalDate statsDate);
}
