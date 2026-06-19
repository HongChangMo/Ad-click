package com.adclick.click.infrastructure;

import com.adclick.click.domain.ClickEvent;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
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

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAnonymousId()).isNull();
    }
}
