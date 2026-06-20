package com.adclick.aggregation.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class ClickDailyStatsId implements Serializable {

    private Long adId;
    private LocalDate statsDate;

    public ClickDailyStatsId() {
    }

    public ClickDailyStatsId(Long adId, LocalDate statsDate) {
        this.adId = adId;
        this.statsDate = statsDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClickDailyStatsId that)) {
            return false;
        }
        return Objects.equals(adId, that.adId)
                && Objects.equals(statsDate, that.statsDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adId, statsDate);
    }
}
