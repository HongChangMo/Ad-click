package com.adclick.management.infrastructure;

import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdStatus;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create")
@Testcontainers
class AdJpaRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private AdJpaRepository adJpaRepository;

    @Test
    void save_and_findById_returns_persisted_ad() {
        Ad ad = Ad.of(1L, "Summer Sale");
        Ad saved = adJpaRepository.save(ad);

        Ad found = adJpaRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getName()).isEqualTo("Summer Sale");
        assertThat(found.getStatus()).isEqualTo(AdStatus.ACTIVE);
        assertThat(found.getAdvertiserId()).isEqualTo(1L);
    }

    @Test
    void findAllIdsByStatus_returns_only_matching_status_ids() {
        Ad active1 = adJpaRepository.save(Ad.of(1L, "Active One"));
        Ad active2 = adJpaRepository.save(Ad.of(1L, "Active Two"));
        Ad paused = Ad.of(1L, "Paused");
        paused.changeStatus(AdStatus.PAUSED);
        adJpaRepository.save(paused);
        Ad exhausted = Ad.of(1L, "Exhausted");
        exhausted.changeStatus(AdStatus.EXHAUSTED);
        adJpaRepository.save(exhausted);

        var activeIds = adJpaRepository.findAllIdsByStatus(AdStatus.ACTIVE);

        assertThat(activeIds).containsExactlyInAnyOrder(active1.getId(), active2.getId());
        assertThat(activeIds).doesNotContain(paused.getId(), exhausted.getId());
    }

    @Test
    void findRandomActive_returns_active_ad_only() {
        Ad active = adJpaRepository.save(Ad.of(1L, "Active"));
        Ad paused = Ad.of(1L, "Paused");
        paused.changeStatus(AdStatus.PAUSED);
        adJpaRepository.save(paused);

        Ad found = adJpaRepository.findRandomActive().orElseThrow();

        assertThat(found.getId()).isEqualTo(active.getId());
        assertThat(found.getStatus()).isEqualTo(AdStatus.ACTIVE);
    }

    @Test
    void findRandomActive_returns_empty_when_no_active_ad_exists() {
        Ad paused = Ad.of(1L, "Paused");
        paused.changeStatus(AdStatus.PAUSED);
        adJpaRepository.save(paused);

        assertThat(adJpaRepository.findRandomActive()).isEmpty();
    }
}
