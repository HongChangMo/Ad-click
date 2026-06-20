package com.adclick.aggregation.application;

import com.adclick.aggregation.domain.ClickDailyStats;
import com.adclick.aggregation.infrastructure.ClickDailyStatsJpaRepository;
import com.adclick.aggregation.infrastructure.ProcessedClickEventJpaRepository;
import com.adclick.aggregation.message.ClickEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create")
@Testcontainers
class ClickAggregationServiceTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    ProcessedClickEventJpaRepository processedRepository;

    @Autowired
    ClickDailyStatsJpaRepository statsRepository;

    @Test
    void aggregate_updates_daily_stats_once_for_same_click_event_id() {
        ClickAggregationService service = new ClickAggregationService(processedRepository, statsRepository);
        ClickEventMessage message = new ClickEventMessage(
                1L,
                10L,
                "1.2.3.4",
                "anon-id",
                true,
                null,
                LocalDateTime.of(2026, 6, 20, 12, 0));

        boolean first = service.aggregate(message);
        boolean duplicate = service.aggregate(message);

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
        ClickDailyStats stats = statsRepository.findByAdIdAndStatsDate(10L, LocalDate.of(2026, 6, 20));
        assertThat(stats.getValidCount()).isEqualTo(1);
        assertThat(stats.getInvalidCount()).isZero();
        assertThat(processedRepository.count()).isEqualTo(1);
    }

    @Test
    void aggregate_counts_invalid_clicks_separately() {
        ClickAggregationService service = new ClickAggregationService(processedRepository, statsRepository);
        ClickEventMessage message = new ClickEventMessage(
                2L,
                10L,
                "1.2.3.4",
                "anon-id",
                false,
                "DUPLICATE_IP",
                LocalDateTime.of(2026, 6, 20, 12, 0));

        boolean processed = service.aggregate(message);

        assertThat(processed).isTrue();
        ClickDailyStats stats = statsRepository.findByAdIdAndStatsDate(10L, LocalDate.of(2026, 6, 20));
        assertThat(stats.getValidCount()).isZero();
        assertThat(stats.getInvalidCount()).isEqualTo(1);
    }
}
