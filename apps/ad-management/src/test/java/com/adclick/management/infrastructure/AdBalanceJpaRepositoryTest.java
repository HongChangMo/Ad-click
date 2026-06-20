package com.adclick.management.infrastructure;

import com.adclick.management.domain.AdBalance;
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

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create")
@Testcontainers
class AdBalanceJpaRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private AdBalanceJpaRepository adBalanceJpaRepository;

    @Test
    void save_and_findById_returns_persisted_balance() {
        AdBalance balance = AdBalance.of(1L);
        balance.add(BigDecimal.valueOf(5000));

        adBalanceJpaRepository.save(balance);

        Optional<AdBalance> found = adBalanceJpaRepository.findById(1L);
        assertThat(found).isPresent();
        assertThat(found.get().getBalance()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    void add_updates_existing_balance() {
        AdBalance balance = AdBalance.of(2L);
        balance.add(BigDecimal.valueOf(1000));
        adBalanceJpaRepository.save(balance);

        AdBalance loaded = adBalanceJpaRepository.findById(2L).get();
        loaded.add(BigDecimal.valueOf(500));
        adBalanceJpaRepository.save(loaded);

        AdBalance updated = adBalanceJpaRepository.findById(2L).get();
        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1500));
    }
}
