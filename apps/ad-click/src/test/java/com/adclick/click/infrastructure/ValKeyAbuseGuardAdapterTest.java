package com.adclick.click.infrastructure;

import com.adclick.click.domain.InvalidClickReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ValKeyAbuseGuardAdapterTest.RedisTestApplication.class)
@TestPropertySource(properties = {
        "adclick.click.abuse-guard.ttl-seconds=60"
})
@Testcontainers
class ValKeyAbuseGuardAdapterTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @Import(ValKeyAbuseGuardAdapter.class)
    static class RedisTestApplication {
    }

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    ValKeyAbuseGuardAdapter adapter;

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanRedis() {
        Set<String> keys = redisTemplate.keys("abuse:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void first_click_marks_ip_and_anonymous_id_as_seen() {
        Optional<InvalidClickReason> result = adapter.checkAndMark(1L, "1.2.3.4", "anon-id");

        assertThat(result).isEmpty();
        assertThat(redisTemplate.hasKey("abuse:ip:1.2.3.4:1")).isTrue();
        assertThat(redisTemplate.hasKey("abuse:anon:anon-id:1")).isTrue();
    }

    @Test
    void duplicate_ip_returns_duplicate_ip_reason() {
        adapter.checkAndMark(1L, "1.2.3.4", "anon-id");

        Optional<InvalidClickReason> result = adapter.checkAndMark(1L, "1.2.3.4", "other-anon");

        assertThat(result).contains(InvalidClickReason.DUPLICATE_IP);
    }

    @Test
    void duplicate_anonymous_id_returns_duplicate_anon_reason() {
        adapter.checkAndMark(1L, "1.2.3.4", "anon-id");

        Optional<InvalidClickReason> result = adapter.checkAndMark(1L, "5.6.7.8", "anon-id");

        assertThat(result).contains(InvalidClickReason.DUPLICATE_ANON);
    }
}
