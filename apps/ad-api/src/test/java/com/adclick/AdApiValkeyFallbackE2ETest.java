package com.adclick;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AdApiValkeyFallbackE2ETest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 1);
        registry.add("spring.data.redis.timeout", () -> "200ms");
        registry.add("spring.data.redis.connect-timeout", () -> "200ms");
        registry.add("spring.kafka.listener.auto-startup", () -> false);
        registry.add("adclick.kafka.outbox.relay.enabled", () -> false);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void click_succeeds_when_valkey_is_unavailable() {
        Long adId = registerAdAndCharge("Valkey Fallback Click Test", 200);

        ResponseEntity<Map> clickResponse = restTemplate.postForEntity(
                "/api/v1/ads/" + adId + "/clicks", null, Map.class);

        assertThat(clickResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clickResponse.getBody().get("isValid")).isEqualTo(true);

        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/balance", Map.class);
        assertThat(((Number) balanceResponse.getBody().get("balance")).intValue()).isEqualTo(150);
    }

    @Test
    void getNextAd_falls_back_to_db_when_valkey_is_unavailable() {
        Long adId = registerAdAndCharge("Valkey Fallback Rotation Test", 200);

        ResponseEntity<Map> nextResponse = restTemplate.getForEntity("/api/v1/ads/next", Map.class);

        assertThat(nextResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) nextResponse.getBody().get("id")).longValue()).isEqualTo(adId);

        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/balance", Map.class);
        assertThat(((Number) balanceResponse.getBody().get("balance")).intValue()).isEqualTo(190);
    }

    @Test
    void reconciliation_invalidates_duplicate_clicks_and_refunds_balance_after_valkey_outage() {
        Long adId = registerAdAndCharge("Valkey Fallback Reconciliation Test", 200);

        ResponseEntity<Map> firstClick = restTemplate.postForEntity(
                "/api/v1/ads/" + adId + "/clicks", null, Map.class);
        ResponseEntity<Map> duplicateClick = restTemplate.postForEntity(
                "/api/v1/ads/" + adId + "/clicks", null, Map.class);
        assertThat(firstClick.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duplicateClick.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstClick.getBody().get("isValid")).isEqualTo(true);
        assertThat(duplicateClick.getBody().get("isValid")).isEqualTo(true);

        ResponseEntity<Map> before = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/balance", Map.class);
        assertThat(((Number) before.getBody().get("balance")).intValue()).isEqualTo(100);

        ResponseEntity<Map> reconciliation = restTemplate.postForEntity(
                "/api/v1/clicks/reconciliation",
                Map.of(
                        "from", "2000-01-01T00:00:00",
                        "to", "2099-01-01T00:00:00"
                ),
                Map.class);

        assertThat(reconciliation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) reconciliation.getBody().get("invalidatedCount")).intValue()).isEqualTo(1);
        assertThat(((Number) reconciliation.getBody().get("refundedAmount")).intValue()).isEqualTo(50);

        ResponseEntity<Map> after = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/balance", Map.class);
        assertThat(((Number) after.getBody().get("balance")).intValue()).isEqualTo(150);

        ResponseEntity<Map> stats = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/clicks/stats", Map.class);
        assertThat(((Number) stats.getBody().get("validCount")).intValue()).isEqualTo(1);
        assertThat(((Number) stats.getBody().get("invalidCount")).intValue()).isEqualTo(1);
    }

    private Long registerAdAndCharge(String name, int amount) {
        Map<String, Object> request = Map.of("advertiserId", 1, "name", name);
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/ads", request, Map.class);
        Long adId = ((Number) response.getBody().get("id")).longValue();
        restTemplate.postForEntity("/api/v1/ads/" + adId + "/balance/charge",
                Map.of("amount", amount), Map.class);
        return adId;
    }
}
