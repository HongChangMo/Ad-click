package com.adclick;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AdApiE2ETest {

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void register_ad_returns_201_with_id() {
        Map<String, Object> request = Map.of("advertiserId", 1, "name", "Summer Sale");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/ads", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void changeStatus_to_paused_and_verify_via_get() {
        // 광고 등록
        Map<String, Object> registerRequest = Map.of("advertiserId", 1, "name", "Winter Sale");
        ResponseEntity<Map> registerResponse = restTemplate.postForEntity(
                "/api/v1/ads", registerRequest, Map.class);
        Long adId = ((Number) registerResponse.getBody().get("id")).longValue();

        // 상태 PAUSED 변경
        Map<String, Object> statusRequest = Map.of("status", "PAUSED");
        ResponseEntity<Void> patchResponse = restTemplate.exchange(
                "/api/v1/ads/" + adId + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(statusRequest),
                Void.class);

        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // GET으로 상태 확인
        ResponseEntity<Map> getResponse = restTemplate.getForEntity("/api/v1/ads/" + adId, Map.class);
        assertThat(getResponse.getBody().get("status")).isEqualTo("PAUSED");
    }

    @Test
    void changeStatus_with_nonexistent_id_returns_404() {
        Map<String, Object> statusRequest = Map.of("status", "PAUSED");
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/ads/99999/status",
                HttpMethod.PATCH,
                new HttpEntity<>(statusRequest),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
