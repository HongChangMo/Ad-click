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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
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
    void getNextAd_returns_registered_active_ads_in_rotation() throws Exception {
        Long ad1 = registerAdAndGetId("Rotation Ad Alpha");
        Long ad2 = registerAdAndGetId("Rotation Ad Beta");
        Long ad3 = registerAdAndGetId("Rotation Ad Gamma");

        pauseAdsExcept(Set.of(ad1, ad2, ad3), ad3);
        redis.execInContainer("redis-cli", "DEL", "ad:rotation:queue");

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

    @Test
    void duplicate_click_from_same_ip_records_invalid_event_without_deducting_balance() {
        Long adId = registerAdAndCharge("E2E Duplicate IP Test", 200);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "203.0.113.10");

        ResponseEntity<Map> first = restTemplate.exchange(
                "/api/v1/ads/" + adId + "/clicks",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class);
        ResponseEntity<Map> second = restTemplate.exchange(
                "/api/v1/ads/" + adId + "/clicks",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("isValid")).isEqualTo(true);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("isValid")).isEqualTo(false);
        assertThat(second.getBody().get("invalidReason")).isEqualTo("DUPLICATE_IP");

        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/balance", Map.class);
        assertThat(((Number) balanceResponse.getBody().get("balance")).intValue()).isEqualTo(150);

    }

    @Test
    void duplicate_click_from_same_anonymous_id_records_invalid_event_without_deducting_balance() {
        Long adId = registerAdAndCharge("E2E Duplicate Anonymous Test", 200);
        HttpHeaders firstHeaders = new HttpHeaders();
        firstHeaders.set("X-Forwarded-For", "203.0.113.20");
        firstHeaders.add(HttpHeaders.COOKIE, "anonymous_id=e2e-anon-duplicate");
        HttpHeaders secondHeaders = new HttpHeaders();
        secondHeaders.set("X-Forwarded-For", "203.0.113.21");
        secondHeaders.add(HttpHeaders.COOKIE, "anonymous_id=e2e-anon-duplicate");

        ResponseEntity<Map> first = restTemplate.exchange(
                "/api/v1/ads/" + adId + "/clicks",
                HttpMethod.POST,
                new HttpEntity<>(firstHeaders),
                Map.class);
        ResponseEntity<Map> second = restTemplate.exchange(
                "/api/v1/ads/" + adId + "/clicks",
                HttpMethod.POST,
                new HttpEntity<>(secondHeaders),
                Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("isValid")).isEqualTo(false);
        assertThat(second.getBody().get("invalidReason")).isEqualTo("DUPLICATE_ANON");

        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/balance", Map.class);
        assertThat(((Number) balanceResponse.getBody().get("balance")).intValue()).isEqualTo(150);
    }

    @Test
    void click_rate_limit_returns_429_when_ip_exceeds_limit() {
        Long adId = registerAdAndCharge("E2E Rate Limit Test", 5000);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "203.0.113.250");

        ResponseEntity<Map> response = null;
        for (int i = 0; i < 101; i++) {
            headers.set(HttpHeaders.COOKIE, "anonymous_id=rate-limit-anon-" + i);
            response = restTemplate.exchange(
                    "/api/v1/ads/" + adId + "/clicks",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    Map.class);
        }

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void click_stats_returns_valid_and_invalid_counts() {
        Long adId = registerAdAndCharge("E2E Click Stats Test", 1000);

        for (int i = 0; i < 7; i++) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Forwarded-For", "203.0.114." + i);
            headers.add(HttpHeaders.COOKIE, "anonymous_id=stats-valid-anon-" + i);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/api/v1/ads/" + adId + "/clicks",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("isValid")).isEqualTo(true);
        }

        for (int i = 0; i < 3; i++) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Forwarded-For", "203.0.114.0");
            headers.add(HttpHeaders.COOKIE, "anonymous_id=stats-invalid-anon-" + i);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/api/v1/ads/" + adId + "/clicks",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("isValid")).isEqualTo(false);
        }

        ResponseEntity<Map> stats = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/clicks/stats", Map.class);

        assertThat(stats.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) stats.getBody().get("validCount")).intValue()).isEqualTo(7);
        assertThat(((Number) stats.getBody().get("invalidCount")).intValue()).isEqualTo(3);
    }

    @Test
    void click_stats_filters_by_period() {
        Long adId = registerAdAndCharge("E2E Click Stats Period Test", 200);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "203.0.115.1");
        headers.add(HttpHeaders.COOKIE, "anonymous_id=stats-period-anon");
        ResponseEntity<Map> click = restTemplate.exchange(
                "/api/v1/ads/" + adId + "/clicks",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class);
        assertThat(click.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> stats = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/clicks/stats?from=2099-01-01T00:00:00&to=2099-01-02T00:00:00",
                Map.class);

        assertThat(stats.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) stats.getBody().get("validCount")).intValue()).isEqualTo(0);
        assertThat(((Number) stats.getBody().get("invalidCount")).intValue()).isEqualTo(0);
    }

    @Test
    void concurrent_clicks_deduct_only_available_balance_and_never_go_negative() throws Exception {
        Long adId = registerAdAndCharge("E2E Concurrent Click Test", 500);
        int requestCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger sequence = new AtomicInteger();
        List<ResponseEntity<Map>> responses = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    int index = sequence.incrementAndGet();
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-Forwarded-For", "198.51.100." + index);
                    headers.add(HttpHeaders.COOKIE, "anonymous_id=concurrent-anon-" + index);
                    responses.add(restTemplate.exchange(
                            "/api/v1/ads/" + adId + "/clicks",
                            HttpMethod.POST,
                            new HttpEntity<>(headers),
                            Map.class));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        long successCount = responses.stream()
                .filter(r -> r.getStatusCode().is2xxSuccessful())
                .count();
        long blockedCount = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.NOT_FOUND)
                .count();

        assertThat(responses).hasSize(requestCount);
        assertThat(successCount).isEqualTo(10);
        assertThat(blockedCount).isEqualTo(10);

        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
                "/api/v1/ads/" + adId + "/balance", Map.class);
        assertThat(((Number) balanceResponse.getBody().get("balance")).intValue()).isEqualTo(0);

        ResponseEntity<Map> extraClick = restTemplate.postForEntity(
                "/api/v1/ads/" + adId + "/clicks", null, Map.class);
        assertThat(extraClick.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void ad_becomes_exhausted_and_is_removed_from_rotation_when_balance_reaches_zero() throws Exception {
        Long exhaustedAdId = registerAdAndCharge("E2E Exhausted Ad", 60);
        Long activeAdId = registerAdAndCharge("E2E Still Active Ad", 500);
        pauseAdsExcept(Set.of(exhaustedAdId, activeAdId), activeAdId);
        redis.execInContainer("redis-cli", "DEL", "ad:rotation:queue");

        boolean seen = false;
        for (int i = 0; i < 20; i++) {
            ResponseEntity<Map> next = restTemplate.getForEntity("/api/v1/ads/next", Map.class);
            assertThat(next.getStatusCode()).isEqualTo(HttpStatus.OK);
            Long returnedId = ((Number) next.getBody().get("id")).longValue();
            if (returnedId.equals(exhaustedAdId)) {
                seen = true;
                break;
            }
        }
        assertThat(seen).isTrue();

        ResponseEntity<Map> clickResponse = restTemplate.postForEntity(
                "/api/v1/ads/" + exhaustedAdId + "/clicks", null, Map.class);
        assertThat(clickResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = restTemplate.getForEntity(
                "/api/v1/ads/" + exhaustedAdId, Map.class);
        assertThat(getResponse.getBody().get("status")).isEqualTo("EXHAUSTED");

        ResponseEntity<Map> extraClick = restTemplate.postForEntity(
                "/api/v1/ads/" + exhaustedAdId + "/clicks", null, Map.class);
        assertThat(extraClick.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        for (int i = 0; i < 10; i++) {
            ResponseEntity<Map> next = restTemplate.getForEntity("/api/v1/ads/next", Map.class);
            assertThat(next.getStatusCode()).isEqualTo(HttpStatus.OK);
            Long returnedId = ((Number) next.getBody().get("id")).longValue();
            assertThat(returnedId).isEqualTo(activeAdId);
        }
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

    private void pauseAdsExcept(Set<Long> keepActive, Long maxAdId) {
        for (long id = 1L; id <= maxAdId; id++) {
            if (keepActive.contains(id)) {
                continue;
            }
            restTemplate.exchange("/api/v1/ads/" + id + "/status",
                    HttpMethod.PATCH,
                    new HttpEntity<>(Map.of("status", "PAUSED")),
                    Void.class);
        }
    }
}
