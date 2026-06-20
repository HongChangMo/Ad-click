package com.adclick.aggregation.application;

import com.adclick.aggregation.domain.ClickDailyStats;
import com.adclick.aggregation.domain.ProcessedClickEvent;
import com.adclick.aggregation.infrastructure.ClickDailyStatsJpaRepository;
import com.adclick.aggregation.infrastructure.ProcessedClickEventJpaRepository;
import com.adclick.aggregation.message.ClickEventMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ClickAggregationService {

    private final ProcessedClickEventJpaRepository processedRepository;
    private final ClickDailyStatsJpaRepository statsRepository;

    public ClickAggregationService(
            ProcessedClickEventJpaRepository processedRepository,
            ClickDailyStatsJpaRepository statsRepository) {
        this.processedRepository = processedRepository;
        this.statsRepository = statsRepository;
    }

    @Transactional
    public boolean aggregate(ClickEventMessage message) {
        return aggregateOne(message);
    }

    @Transactional
    public int aggregateAll(List<ClickEventMessage> messages) {
        int processedCount = 0;
        for (ClickEventMessage message : messages) {
            if (aggregateOne(message)) {
                processedCount++;
            }
        }
        return processedCount;
    }

    private boolean aggregateOne(ClickEventMessage message) {
        if (processedRepository.existsById(message.clickEventId())) {
            return false;
        }
        try {
            processedRepository.saveAndFlush(ProcessedClickEvent.of(message.clickEventId()));
        } catch (DataIntegrityViolationException e) {
            return false;
        }

        LocalDate statsDate = message.clickedAt().toLocalDate();
        ClickDailyStats stats = statsRepository.findByAdIdAndStatsDate(message.adId(), statsDate);
        if (stats == null) {
            stats = ClickDailyStats.of(message.adId(), statsDate);
        }
        stats.increment(message.valid());
        statsRepository.save(stats);
        return true;
    }
}
