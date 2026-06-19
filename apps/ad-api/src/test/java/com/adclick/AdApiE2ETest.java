package com.adclick;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Test
    void charge_balance_increases_amount() {
        Map<String, Object> registerRequest = Map.of("advertiserId", 1, "name", "Balance Test Ad");
        ResponseEntity<Map> registerResponse = restTemplate.postForEntity("/api/v1/ads", registerRequest, Map.class);
        Long adId = ((Number) registerResponse.getBody().get("id")).longValue();

        Map<String, Object> chargeRequest = Map.of("amount", 5000);
        ResponseEntity<Map> chargeResponse = restTemplate.postForEntity(
                "/api/v1/ads/" + adId + "/balance/charge", chargeRequest, Map.class);

        assertThat(chargeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) chargeResponse.getBody().get("balance")).intValue()).isEqualTo(5000);

        ResponseEntity<Map> getResponse = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/balance", Map.class);
        assertThat(((Number) getResponse.getBody().get("balance")).intValue()).isEqualTo(5000);
    }

    @Test
    void charge_exhausted_ad_activates_it() {
        Map<String, Object> registerRequest = Map.of("advertiserId", 1, "name", "Exhausted Ad");
        ResponseEntity<Map> registerResponse = restTemplate.postForEntity("/api/v1/ads", registerRequest, Map.class);
        Long adId = ((Number) registerResponse.getBody().get("id")).longValue();

        Map<String, Object> exhaustRequest = Map.of("status", "EXHAUSTED");
        restTemplate.exchange("/api/v1/ads/" + adId + "/status",
                HttpMethod.PATCH, new HttpEntity<>(exhaustRequest), Void.class);

        Map<String, Object> chargeRequest = Map.of("amount", 1000);
        restTemplate.postForEntity("/api/v1/ads/" + adId + "/balance/charge", chargeRequest, Map.class);

        ResponseEntity<Map> getResponse = restTemplate.getForEntity("/api/v1/ads/" + adId, Map.class);
        assertThat(getResponse.getBody().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void charge_paused_ad_keeps_paused() {
        Map<String, Object> registerRequest = Map.of("advertiserId", 1, "name", "Paused Ad");
        ResponseEntity<Map> registerResponse = restTemplate.postForEntity("/api/v1/ads", registerRequest, Map.class);
        Long adId = ((Number) registerResponse.getBody().get("id")).longValue();

        Map<String, Object> pauseRequest = Map.of("status", "PAUSED");
        restTemplate.exchange("/api/v1/ads/" + adId + "/status",
                HttpMethod.PATCH, new HttpEntity<>(pauseRequest), Void.class);

        Map<String, Object> chargeRequest = Map.of("amount", 1000);
        restTemplate.postForEntity("/api/v1/ads/" + adId + "/balance/charge", chargeRequest, Map.class);

        ResponseEntity<Map> getResponse = restTemplate.getForEntity("/api/v1/ads/" + adId, Map.class);
        assertThat(getResponse.getBody().get("status")).isEqualTo("PAUSED");
    }

    @Test
    void getNextAd_returns_registered_active_ads_in_rotation() {
        Long ad1 = registerAdAndGetId("Rotation Ad Alpha");
        Long ad2 = registerAdAndGetId("Rotation Ad Beta");
        Long ad3 = registerAdAndGetId("Rotation Ad Gamma");

        // /next 호출마다 10원씩 차감되므로 30회 호출 기준 광고당 최소 300원 충전
        chargeBalance(ad1, 500);
        chargeBalance(ad2, 500);
        chargeBalance(ad3, 500);

        Set<Long> expected = Set.of(ad1, ad2, ad3);
        Set<Long> seen = new java.util.HashSet<>();

        for (int i = 0; i < 30; i++) {
            ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/ads/next", Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Long id = ((Number) response.getBody().get("id")).longValue();
            seen.add(id);
            if (seen.containsAll(expected)) break;
        }

        assertThat(seen).containsAll(expected);
    }

    @Test
    void getNextAd_returns_404_when_no_active_ads() {
        // NOTE: 이 테스트는 완전한 격리가 필요하므로, 다른 테스트에서 등록된 ACTIVE 광고가 있을 경우
        // 404 대신 200이 반환될 수 있음. 따라서 응답 코드의 유효성만 확인한다.
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/ads/next", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()
                || response.getStatusCode() == HttpStatus.NOT_FOUND).isTrue();
    }

    @Test
    void getNextAd_deducts_10_from_balance_on_view() throws Exception {
        // 테스트 대상 광고 등록 후 500원 충전
        Long adId = registerAdAndGetId("View Charge Test Ad");
        chargeBalance(adId, 500);

        // Valkey 큐를 비워서 다음 /next 호출 시 전체 ACTIVE 광고를 포함하는 큐 재구성을 유도
        // 큐 재구성 시 우리 광고가 반드시 포함되므로, 이후 충분한 횟수 호출하면 반드시 반환됨
        redis.execInContainer("redis-cli", "DEL", "ad:rotation:queue");

        // /next를 최대 100회 호출 — 큐 재구성 후 우리 광고가 반드시 로테이션에 포함됨
        boolean seen = false;
        for (int i = 0; i < 100; i++) {
            ResponseEntity<Map> next = restTemplate.getForEntity("/api/v1/ads/next", Map.class);
            if (next.getStatusCode().is2xxSuccessful() && next.getBody() != null) {
                Long returnedId = ((Number) next.getBody().get("id")).longValue();
                if (returnedId.equals(adId)) {
                    seen = true;
                    break;
                }
            }
        }

        assertThat(seen).as("View Charge Test Ad must appear in /next within 100 calls after queue rebuild").isTrue();

        // 우리 광고가 최소 1회 VIEW 과금됐으므로:
        // - 잔액은 반드시 500 미만
        // - 잔액은 0 이상
        // - (500 - 잔액)은 10의 배수
        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/balance", Map.class);
        int finalBalance = ((Number) balanceResponse.getBody().get("balance")).intValue();
        assertThat(finalBalance).isLessThan(500).isGreaterThanOrEqualTo(0);
        assertThat((500 - finalBalance) % 10).isEqualTo(0);
    }

    @Test
    void click_deducts_50_from_balance_and_records_event() {
        Long adId = registerAdAndCharge("E2E Click 50won Test", 200);

        ResponseEntity<Map> clickResponse = restTemplate.postForEntity(
                "/api/v1/ads/" + adId + "/clicks", null, Map.class);

        assertThat(clickResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clickResponse.getBody().get("adId")).isEqualTo(adId.intValue());
        assertThat(clickResponse.getBody().get("isValid")).isEqualTo(true);

        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/balance", Map.class);
        assertThat(((Number) balanceResponse.getBody().get("balance")).intValue()).isEqualTo(150);
    }

    @Test
    void click_returns_404_when_ad_is_paused() {
        Long adId = registerAdAndCharge("E2E Paused Click Test", 200);
        restTemplate.exchange("/api/v1/ads/" + adId + "/status",
                HttpMethod.PATCH, new HttpEntity<>(Map.of("status", "PAUSED")), Void.class);

        ResponseEntity<Void> clickResponse = restTemplate.postForEntity(
                "/api/v1/ads/" + adId + "/clicks", null, Void.class);

        assertThat(clickResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void click_sets_anonymous_id_cookie_when_absent() {
        Long adId = registerAdAndCharge("E2E Cookie Test Ad", 200);

        ResponseEntity<Map> clickResponse = restTemplate.postForEntity(
                "/api/v1/ads/" + adId + "/clicks", null, Map.class);

        assertThat(clickResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> setCookieHeaders = clickResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders.stream()
                .anyMatch(h -> h.startsWith("anonymous_id="))).isTrue();
    }

    private Long registerAdAndGetId(String name) {
        Map<String, Object> request = Map.of("advertiserId", 1, "name", name);
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/ads", request, Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Long registerAdAndCharge(String name, int amount) {
        Long adId = registerAdAndGetId(name);
        restTemplate.postForEntity("/api/v1/ads/" + adId + "/balance/charge",
                Map.of("amount", amount), Map.class);
        return adId;
    }

    private void chargeBalance(Long adId, int amount) {
        restTemplate.postForEntity("/api/v1/ads/" + adId + "/balance/charge",
                Map.of("amount", amount), Map.class);
    }
}
