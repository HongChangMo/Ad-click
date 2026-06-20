package com.adclick.aggregation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "click_daily_stats")
@IdClass(ClickDailyStatsId.class)
public class ClickDailyStats {

    @Id
    private Long adId;

    @Id
    private LocalDate statsDate;

    @Column(nullable = false)
    private long validCount;

    @Column(nullable = false)
    private long invalidCount;

    protected ClickDailyStats() {
    }

    public static ClickDailyStats of(Long adId, LocalDate statsDate) {
        ClickDailyStats stats = new ClickDailyStats();
        stats.adId = adId;
        stats.statsDate = statsDate;
        stats.validCount = 0;
        stats.invalidCount = 0;
        return stats;
    }

    public void increment(boolean valid) {
        if (valid) {
            validCount++;
            return;
        }
        invalidCount++;
    }

    public Long getAdId() {
        return adId;
    }

    public LocalDate getStatsDate() {
        return statsDate;
    }

    public long getValidCount() {
        return validCount;
    }

    public long getInvalidCount() {
        return invalidCount;
    }

}
