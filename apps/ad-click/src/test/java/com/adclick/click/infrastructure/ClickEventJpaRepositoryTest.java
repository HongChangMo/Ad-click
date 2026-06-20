package com.adclick.click.infrastructure;

import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.InvalidClickReason;
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

import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create")
@Testcontainers
class ClickEventJpaRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private ClickEventJpaRepository clickEventJpaRepository;

    @Test
    void save_valid_click_event_and_find_by_id() {
        ClickEvent event = ClickEvent.valid(1L, "192.168.0.1", "anon-uuid-123");

        ClickEvent saved = clickEventJpaRepository.save(event);

        Optional<ClickEvent> found = clickEventJpaRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAdId()).isEqualTo(1L);
        assertThat(found.get().getIpAddress()).isEqualTo("192.168.0.1");
        assertThat(found.get().getAnonymousId()).isEqualTo("anon-uuid-123");
        assertThat(found.get().isValid()).isTrue();
        assertThat(found.get().getInvalidReason()).isNull();
    }

    @Test
    void save_click_event_without_anonymous_id() {
        ClickEvent event = ClickEvent.valid(2L, "10.0.0.1", null);

        ClickEvent saved = clickEventJpaRepository.save(event);

        Optional<ClickEvent> found = clickEventJpaRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAnonymousId()).isNull();
        assertThat(found.get().getClickedAt()).isNotNull();
    }

    @Test
    void save_invalid_click_event_with_reason() {
        ClickEvent event = ClickEvent.invalid(3L, "10.0.0.2", "anon-duplicate", InvalidClickReason.DUPLICATE_ANON);

        ClickEvent saved = clickEventJpaRepository.save(event);

        Optional<ClickEvent> found = clickEventJpaRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().isValid()).isFalse();
        assertThat(found.get().getInvalidReason()).isEqualTo("DUPLICATE_ANON");
    }

    @Test
    void count_by_ad_id_validity_and_clicked_at_between() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 20, 12, 0);
        clickEventJpaRepository.save(ClickEvent.validAt(1L, "10.0.0.1", "anon-1", base.minusDays(1)));
        clickEventJpaRepository.save(ClickEvent.validAt(1L, "10.0.0.2", "anon-2", base.plusHours(1)));
        clickEventJpaRepository.save(ClickEvent.validAt(1L, "10.0.0.3", "anon-3", base.plusHours(2)));
        clickEventJpaRepository.save(ClickEvent.invalidAt(
                1L, "10.0.0.4", "anon-4", InvalidClickReason.DUPLICATE_IP, base.plusHours(3)));
        clickEventJpaRepository.save(ClickEvent.invalidAt(
                1L, "10.0.0.5", "anon-5", InvalidClickReason.DUPLICATE_ANON, base.plusDays(1)));
        clickEventJpaRepository.save(ClickEvent.validAt(2L, "10.0.0.6", "anon-6", base.plusHours(1)));

        long validCount = clickEventJpaRepository.countByAdIdAndIsValidAndClickedAtBetween(
                1L, true, base, base.plusHours(23));
        long invalidCount = clickEventJpaRepository.countByAdIdAndIsValidAndClickedAtBetween(
                1L, false, base, base.plusHours(23));

        assertThat(validCount).isEqualTo(2);
        assertThat(invalidCount).isEqualTo(1);
    }

    @Test
    void find_valid_events_between_orders_for_reconciliation_scan() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 20, 12, 0);
        ClickEvent outside = clickEventJpaRepository.save(
                ClickEvent.validAt(1L, "10.0.0.1", "outside", base.minusMinutes(1)));
        ClickEvent laterSameIp = clickEventJpaRepository.save(
                ClickEvent.validAt(1L, "10.0.0.1", "later", base.plusMinutes(2)));
        ClickEvent firstSameIp = clickEventJpaRepository.save(
                ClickEvent.validAt(1L, "10.0.0.1", "first", base.plusMinutes(1)));
        ClickEvent invalid = clickEventJpaRepository.save(ClickEvent.invalidAt(
                1L, "10.0.0.2", "invalid", InvalidClickReason.DUPLICATE_IP, base.plusMinutes(3)));
        ClickEvent otherAd = clickEventJpaRepository.save(
                ClickEvent.validAt(2L, "10.0.0.1", "other-ad", base.plusMinutes(1)));

        var result = clickEventJpaRepository
                .findByIsValidTrueAndClickedAtBetweenOrderByAdIdAscIpAddressAscClickedAtAscIdAsc(
                        base, base.plusHours(1));

        assertThat(result).containsExactly(firstSameIp, laterSameIp, otherAd);
        assertThat(result).doesNotContain(outside, invalid);
    }
}
