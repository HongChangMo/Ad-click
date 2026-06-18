package com.adclick.management.infrastructure;

import com.adclick.management.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Testcontainers
class ValKeyRotationAdapterTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    ValKeyRotationAdapter adapter;

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.delete("ad:rotation:queue");
        redisTemplate.delete("ad:rotation:rebuild:lock");
    }

    @Test
    void offer_and_poll_maintains_fifo_order() {
        adapter.offer(1L);
        adapter.offer(2L);
        adapter.offer(3L);

        assertThat(adapter.poll()).isEqualTo(Optional.of(1L));
        assertThat(adapter.poll()).isEqualTo(Optional.of(2L));
        assertThat(adapter.poll()).isEqualTo(Optional.of(3L));
        assertThat(adapter.poll()).isEqualTo(Optional.empty());
    }

    @Test
    void offer_and_poll_implements_round_robin_when_rpushed_back() {
        adapter.offer(1L);
        adapter.offer(2L);

        Long first = adapter.poll().orElseThrow();
        adapter.offer(first);

        assertThat(adapter.poll()).isEqualTo(Optional.of(2L));
        assertThat(adapter.poll()).isEqualTo(Optional.of(1L));
    }

    @Test
    void tryRebuildLock_returns_true_first_time_false_second() {
        assertThat(adapter.tryRebuildLock()).isTrue();
        assertThat(adapter.tryRebuildLock()).isFalse();
    }

    @Test
    void releaseRebuildLock_allows_reacquire() {
        adapter.tryRebuildLock();
        adapter.releaseRebuildLock();

        assertThat(adapter.tryRebuildLock()).isTrue();
    }
}
