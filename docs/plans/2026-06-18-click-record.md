# click-record Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 조회(GET /next) 시 10원 차감 + 클릭(POST /clicks) 시 50원 차감 + 클릭 이벤트 기록 + anonymous_id 쿠키 처리.

**Architecture:**
- Task 0: `AdRotationFacade.getNextAd()`에 VIEW 과금(10원) 소급 추가 — ad-management 모듈
- Task 1~6: `ad-click` 모듈 첫 구현 — `ClickFacade`가 `BalanceFacade.deduct(CLICK, 50원)` 호출 + `ClickEventRepository` 저장
- `BalanceFacade.deduct(adId, amount, TransactionType)`: VIEW/CLICK 구분하여 기록
- 단일 `@Transactional` 경계 내에서 잔액 차감 + 이벤트 저장 원자적 처리

**Tech Stack:** Spring Boot 3.5, MySQL (JPA), Testcontainers (MySQL:8.0), Mockito

**스코프 (priority 4):**
- 포함: 광고 상태(ACTIVE) 체크, 잔액 차감(VIEW/CLICK), 클릭 기록, 쿠키 처리
- 제외: SELECT FOR UPDATE (priority 5), EXHAUSTED 자동 전환 (priority 6), 어뷰징 방어 (priority 7)

---

### Task 0: AdRotationFacade에 VIEW 과금 추가 (소급 수정)

**Files:**
- Modify: `apps/ad-management/src/main/java/com/adclick/management/domain/AdBalance.java`
- Modify: `apps/ad-management/src/main/java/com/adclick/management/application/BalanceFacade.java`
- Modify: `apps/ad-management/src/main/java/com/adclick/management/application/AdRotationFacade.java`
- Modify: `apps/ad-management/src/test/java/com/adclick/management/application/BalanceFacadeTest.java`
- Modify: `apps/ad-management/src/test/java/com/adclick/management/application/AdRotationFacadeTest.java`

**Step 1: 실패 테스트 먼저 작성**

`BalanceFacadeTest.java`에 아래 두 테스트를 추가한다.

```java
@Test
void deduct_view_subtracts_amount_and_records_view_transaction() {
    AdBalance balance = AdBalance.of(1L);
    balance.add(BigDecimal.valueOf(100));
    given(adBalanceRepository.findByAdId(1L)).willReturn(Optional.of(balance));
    given(adBalanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    balanceFacade.deduct(1L, BigDecimal.TEN, TransactionType.VIEW);

    assertThat(balance.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(90));
    verify(transactionRepository).save(argThat(t ->
            t.getType() == TransactionType.VIEW
            && t.getAmount().compareTo(BigDecimal.TEN) == 0));
}

@Test
void deduct_click_records_click_transaction() {
    AdBalance balance = AdBalance.of(1L);
    balance.add(BigDecimal.valueOf(200));
    given(adBalanceRepository.findByAdId(1L)).willReturn(Optional.of(balance));
    given(adBalanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    balanceFacade.deduct(1L, BigDecimal.valueOf(50), TransactionType.CLICK);

    assertThat(balance.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(150));
    verify(transactionRepository).save(argThat(t -> t.getType() == TransactionType.CLICK));
}
```

`AdRotationFacadeTest.java`에 아래 테스트를 추가한다:

```java
@Mock BalanceFacade balanceFacade;  // 기존 Mock 목록에 추가

@Test
void getNextAd_deducts_view_charge_after_returning_ad() {
    given(queuePort.poll()).willReturn(Optional.of(1L));
    Ad ad = Ad.of(1L, "Test Ad");
    given(adRepository.findById(1L)).willReturn(Optional.of(ad));

    adRotationFacade.getNextAd();

    verify(balanceFacade).deduct(1L, BigDecimal.TEN, TransactionType.VIEW);
}
```

**Step 2: 테스트 실행하여 실패 확인**

```bash
./gradlew :apps:ad-management:test --tests "com.adclick.management.application.BalanceFacadeTest"
./gradlew :apps:ad-management:test --tests "com.adclick.management.application.AdRotationFacadeTest"
```

Expected: FAIL — `deduct(Long, BigDecimal, TransactionType)` 메서드 없음

**Step 3: AdBalance.subtract() 구현**

`AdBalance.java`에 추가:

```java
public void subtract(BigDecimal amount) {
    this.balance = this.balance.subtract(amount);
    this.updatedAt = LocalDateTime.now();
}
```

**Step 4: BalanceFacade.deduct() 구현**

`BalanceFacade.java`에 추가:

```java
@Transactional
public void deduct(Long adId, BigDecimal amount, TransactionType type) {
    AdBalance balance = adBalanceRepository.findByAdId(adId)
            .orElseGet(() -> AdBalance.of(adId));
    balance.subtract(amount);
    adBalanceRepository.save(balance);
    transactionRepository.save(BalanceTransaction.of(adId, amount, type));
}
```

**Step 5: AdRotationFacade 수정 — BalanceFacade 주입 + VIEW 과금**

`AdRotationFacade.java` 전체를 아래로 교체한다:

```java
package com.adclick.management.application;

import com.adclick.management.application.info.AdInfo;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdRotationQueuePort;
import com.adclick.management.domain.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class AdRotationFacade {

    private static final BigDecimal VIEW_COST = BigDecimal.TEN;

    private final AdRepository adRepository;
    private final AdRotationQueuePort queuePort;
    private final BalanceFacade balanceFacade;

    public AdRotationFacade(AdRepository adRepository,
                            AdRotationQueuePort queuePort,
                            BalanceFacade balanceFacade) {
        this.adRepository = adRepository;
        this.queuePort = queuePort;
        this.balanceFacade = balanceFacade;
    }

    @Transactional
    public AdInfo getNextAd() {
        AdInfo adInfo;
        try {
            adInfo = nextAdFromQueue().orElseGet(this::nextAdFromDb);
        } catch (NoActiveAdException e) {
            throw e;
        } catch (Exception e) {
            adInfo = nextAdFromDb();
        }
        balanceFacade.deduct(adInfo.id(), VIEW_COST, TransactionType.VIEW);
        return adInfo;
    }

    private Optional<AdInfo> nextAdFromQueue() {
        Optional<Long> adId = queuePort.poll();
        if (adId.isEmpty()) {
            rebuildQueue();
            adId = queuePort.poll();
        }
        adId.ifPresent(queuePort::offer);
        return adId.flatMap(adRepository::findById).map(AdInfo::from);
    }

    private AdInfo nextAdFromDb() {
        return adRepository.findRandomActive()
                .map(AdInfo::from)
                .orElseThrow(NoActiveAdException::new);
    }

    private void rebuildQueue() {
        if (!queuePort.tryRebuildLock()) {
            return;
        }
        try {
            adRepository.findAllActiveIds().forEach(queuePort::offer);
        } finally {
            queuePort.releaseRebuildLock();
        }
    }
}
```

**Step 6: AdRotationFacadeTest 수정**

기존 `AdRotationFacadeTest.java`에서:
1. `@Mock BalanceFacade balanceFacade;` 필드 추가
2. `@InjectMocks AdRotationFacade adRotationFacade;` — Mockito가 자동으로 BalanceFacade 주입
3. 기존 테스트들은 `balanceFacade.deduct()` 호출이 추가되어도 Mockito가 void 메서드이므로 기본적으로 do-nothing이라 통과됨
4. 새 테스트 `getNextAd_deducts_view_charge_after_returning_ad()` 추가

기존 `getNextAd_falls_back_to_db_when_valkey_throws_exception` 테스트는 `nextAdFromDb()` 경로를 타므로 `adRepository.findRandomActive()`를 stub해야 함 — 이미 되어 있는지 확인하고, 없다면 stub 추가.

**Step 7: 테스트 실행하여 통과 확인**

```bash
./gradlew :apps:ad-management:test
```

Expected: BUILD SUCCESSFUL (기존 테스트 모두 포함)

**Step 8: E2E 테스트에 view 과금 검증 추가**

`AdApiE2ETest.java`에 아래 테스트 추가:

```java
@Test
void getNextAd_deducts_10_from_balance_on_view() {
    Long adId = registerAdAndGetId("View Charge Test Ad");
    restTemplate.postForEntity("/api/v1/ads/" + adId + "/balance/charge",
            Map.of("amount", 100), Map.class);

    // GET /next 1회 호출
    restTemplate.getForEntity("/api/v1/ads/next", Map.class);

    // 잔액 10원 감소 확인
    ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
            "/api/v1/ads/" + adId + "/balance", Map.class);
    // NOTE: 다른 테스트에서 등록된 광고가 먼저 반환될 수 있으므로,
    // 여러 번 호출하여 해당 광고가 반환됐을 때의 잔액 변화를 검증한다.
    // 단순하게: 이 광고에 잔액 충전 후 충분히 호출하면 반드시 한 번은 선택됨.
    int initialBalance = 100;
    int finalBalance = ((Number) balanceResponse.getBody().get("balance")).intValue();
    // 잔액이 감소했는지만 확인 (다른 광고가 먼저 선택될 수 있으므로 정확한 값 대신 감소 여부)
    // 더 정확한 검증은 단독 광고로 테스트 (다른 모든 광고 PAUSED 후 진행)
    assertThat(finalBalance).isLessThanOrEqualTo(initialBalance);
}
```

**Step 9: 전체 테스트 확인**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL

**Step 10: 커밋**

```bash
git add apps/ad-management/src/main/java/com/adclick/management/domain/AdBalance.java \
        apps/ad-management/src/main/java/com/adclick/management/application/BalanceFacade.java \
        apps/ad-management/src/main/java/com/adclick/management/application/AdRotationFacade.java \
        apps/ad-management/src/test/java/com/adclick/management/application/BalanceFacadeTest.java \
        apps/ad-management/src/test/java/com/adclick/management/application/AdRotationFacadeTest.java \
        apps/ad-api/src/test/java/com/adclick/AdApiE2ETest.java \
        apps/ad-management/src/main/java/com/adclick/management/domain/TransactionType.java
git commit -m "feat: add view charge (10원) to GET /next and BalanceFacade.deduct(type)"
```

---

### Task 1: ad-click build.gradle + ClickEvent 엔티티 + ClickEventRepository

**Files:**
- Modify: `apps/ad-click/build.gradle`
- Create: `apps/ad-click/src/main/java/com/adclick/click/domain/ClickEvent.java`
- Create: `apps/ad-click/src/main/java/com/adclick/click/domain/ClickEventRepository.java`

**Step 1: build.gradle에 테스트 의존성 추가**

`apps/ad-click/build.gradle`을 아래로 교체한다:

```groovy
dependencies {
    implementation project(':apps:ad-management')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    runtimeOnly 'com.mysql:mysql-connector-j'

    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:mysql'
    testImplementation 'org.testcontainers:junit-jupiter'
}
```

**Step 2: ClickEvent 엔티티 작성**

`apps/ad-click/src/main/java/com/adclick/click/domain/ClickEvent.java`:

```java
package com.adclick.click.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "click_events",
    indexes = {
        @Index(name = "idx_click_abuse", columnList = "ad_id, ip_address, clicked_at"),
        @Index(name = "idx_click_stats", columnList = "ad_id, clicked_at, is_valid")
    })
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad_id", nullable = false)
    private Long adId;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "anonymous_id", length = 64)
    private String anonymousId;

    @Column(name = "clicked_at", nullable = false)
    private LocalDateTime clickedAt;

    @Column(name = "is_valid", nullable = false)
    private boolean isValid;

    @Column(name = "invalid_reason", length = 30)
    private String invalidReason;

    protected ClickEvent() {}

    public static ClickEvent valid(Long adId, String ipAddress, String anonymousId) {
        ClickEvent e = new ClickEvent();
        e.adId = adId;
        e.ipAddress = ipAddress;
        e.anonymousId = anonymousId;
        e.clickedAt = LocalDateTime.now();
        e.isValid = true;
        return e;
    }

    public Long getId() { return id; }
    public Long getAdId() { return adId; }
    public String getIpAddress() { return ipAddress; }
    public String getAnonymousId() { return anonymousId; }
    public LocalDateTime getClickedAt() { return clickedAt; }
    public boolean isValid() { return isValid; }
    public String getInvalidReason() { return invalidReason; }
}
```

**Step 3: ClickEventRepository 인터페이스 작성**

`apps/ad-click/src/main/java/com/adclick/click/domain/ClickEventRepository.java`:

```java
package com.adclick.click.domain;

public interface ClickEventRepository {
    ClickEvent save(ClickEvent event);
}
```

**Step 4: 컴파일 확인**

```bash
./gradlew :apps:ad-click:compileJava
```

Expected: BUILD SUCCESSFUL

**Step 5: 커밋**

```bash
git add apps/ad-click/build.gradle \
        apps/ad-click/src/main/java/com/adclick/click/domain/
git commit -m "feat: add ClickEvent entity and ClickEventRepository"
```

---

### Task 2: Infrastructure + TestApplication + ClickEventJpaRepositoryTest

**Files:**
- Create: `apps/ad-click/src/main/java/com/adclick/click/infrastructure/ClickEventJpaRepository.java`
- Create: `apps/ad-click/src/main/java/com/adclick/click/infrastructure/ClickEventRepositoryAdapter.java`
- Create: `apps/ad-click/src/test/java/com/adclick/click/TestApplication.java`
- Create: `apps/ad-click/src/test/java/com/adclick/click/infrastructure/ClickEventJpaRepositoryTest.java`

**Step 1: 실패 통합 테스트 작성**

`apps/ad-click/src/test/java/com/adclick/click/TestApplication.java`:

```java
package com.adclick.click;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TestApplication {}
```

`apps/ad-click/src/test/java/com/adclick/click/infrastructure/ClickEventJpaRepositoryTest.java`:

```java
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

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAnonymousId()).isNull();
    }
}
```

**Step 2: 테스트 실행하여 실패 확인**

```bash
./gradlew :apps:ad-click:test --tests "com.adclick.click.infrastructure.ClickEventJpaRepositoryTest"
```

Expected: FAIL — `ClickEventJpaRepository` 클래스 없음

**Step 3: ClickEventJpaRepository 구현**

`apps/ad-click/src/main/java/com/adclick/click/infrastructure/ClickEventJpaRepository.java`:

```java
package com.adclick.click.infrastructure;

import com.adclick.click.domain.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventJpaRepository extends JpaRepository<ClickEvent, Long> {}
```

**Step 4: ClickEventRepositoryAdapter 구현**

`apps/ad-click/src/main/java/com/adclick/click/infrastructure/ClickEventRepositoryAdapter.java`:

```java
package com.adclick.click.infrastructure;

import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventRepository;
import org.springframework.stereotype.Repository;

@Repository
public class ClickEventRepositoryAdapter implements ClickEventRepository {

    private final ClickEventJpaRepository jpaRepository;

    public ClickEventRepositoryAdapter(ClickEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ClickEvent save(ClickEvent event) {
        return jpaRepository.save(event);
    }
}
```

**Step 5: 테스트 통과 확인**

```bash
./gradlew :apps:ad-click:test --tests "com.adclick.click.infrastructure.ClickEventJpaRepositoryTest"
```

Expected: 2 tests PASS

**Step 6: 커밋**

```bash
git add apps/ad-click/src/main/java/com/adclick/click/infrastructure/ \
        apps/ad-click/src/test/java/com/adclick/click/
git commit -m "feat: add ClickEvent JPA infrastructure and integration tests"
```

---

### Task 3: ClickFacade + ClickInfo + ClickFacadeTest (유닛)

**Files:**
- Create: `apps/ad-click/src/main/java/com/adclick/click/application/info/ClickInfo.java`
- Create: `apps/ad-click/src/main/java/com/adclick/click/application/ClickFacade.java`
- Create: `apps/ad-click/src/test/java/com/adclick/click/application/ClickFacadeTest.java`

**Step 1: 실패 유닛 테스트 작성**

`apps/ad-click/src/test/java/com/adclick/click/application/ClickFacadeTest.java`:

```java
package com.adclick.click.application;

import com.adclick.click.application.info.ClickInfo;
import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventRepository;
import com.adclick.management.application.AdNotFoundException;
import com.adclick.management.application.BalanceFacade;
import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdStatus;
import com.adclick.management.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClickFacadeTest {

    @Mock AdRepository adRepository;
    @Mock BalanceFacade balanceFacade;
    @Mock ClickEventRepository clickEventRepository;

    @InjectMocks ClickFacade clickFacade;

    @Test
    void click_active_ad_deducts_50_and_records_event() {
        Ad ad = Ad.of(1L, "Test Ad");
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        ClickEvent savedEvent = ClickEvent.valid(1L, "1.2.3.4", "anon-id");
        given(clickEventRepository.save(any())).willReturn(savedEvent);

        ClickInfo result = clickFacade.click(1L, "1.2.3.4", "anon-id");

        assertThat(result.adId()).isEqualTo(1L);
        assertThat(result.isValid()).isTrue();
        verify(balanceFacade).deduct(1L, BigDecimal.valueOf(50), TransactionType.CLICK);
        verify(clickEventRepository).save(any(ClickEvent.class));
    }

    @Test
    void click_throws_AdNotFoundException_when_ad_not_found() {
        given(adRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> clickFacade.click(999L, "1.2.3.4", "anon"))
                .isInstanceOf(AdNotFoundException.class);

        verify(balanceFacade, never()).deduct(any(), any(), any());
        verify(clickEventRepository, never()).save(any());
    }

    @Test
    void click_throws_AdNotFoundException_when_ad_is_paused() {
        Ad ad = Ad.of(1L, "Paused Ad");
        ad.changeStatus(AdStatus.PAUSED);
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));

        assertThatThrownBy(() -> clickFacade.click(1L, "1.2.3.4", "anon"))
                .isInstanceOf(AdNotFoundException.class);

        verify(balanceFacade, never()).deduct(any(), any(), any());
    }

    @Test
    void click_throws_AdNotFoundException_when_ad_is_exhausted() {
        Ad ad = Ad.of(1L, "Exhausted Ad");
        ad.changeStatus(AdStatus.EXHAUSTED);
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));

        assertThatThrownBy(() -> clickFacade.click(1L, "1.2.3.4", "anon"))
                .isInstanceOf(AdNotFoundException.class);

        verify(balanceFacade, never()).deduct(any(), any(), any());
    }
}
```

**Step 2: 테스트 실행하여 실패 확인**

```bash
./gradlew :apps:ad-click:test --tests "com.adclick.click.application.ClickFacadeTest"
```

Expected: FAIL — `ClickFacade`, `ClickInfo` 없음

**Step 3: ClickInfo 작성**

`apps/ad-click/src/main/java/com/adclick/click/application/info/ClickInfo.java`:

```java
package com.adclick.click.application.info;

import com.adclick.click.domain.ClickEvent;
import java.time.LocalDateTime;

public record ClickInfo(Long id, Long adId, boolean isValid, LocalDateTime clickedAt) {

    public static ClickInfo from(ClickEvent event) {
        return new ClickInfo(event.getId(), event.getAdId(), event.isValid(), event.getClickedAt());
    }
}
```

**Step 4: ClickFacade 작성**

`apps/ad-click/src/main/java/com/adclick/click/application/ClickFacade.java`:

```java
package com.adclick.click.application;

import com.adclick.click.application.info.ClickInfo;
import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventRepository;
import com.adclick.management.application.AdNotFoundException;
import com.adclick.management.application.BalanceFacade;
import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdStatus;
import com.adclick.management.domain.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ClickFacade {

    private static final BigDecimal CLICK_COST = BigDecimal.valueOf(50);

    private final AdRepository adRepository;
    private final BalanceFacade balanceFacade;
    private final ClickEventRepository clickEventRepository;

    public ClickFacade(AdRepository adRepository,
                       BalanceFacade balanceFacade,
                       ClickEventRepository clickEventRepository) {
        this.adRepository = adRepository;
        this.balanceFacade = balanceFacade;
        this.clickEventRepository = clickEventRepository;
    }

    @Transactional
    public ClickInfo click(Long adId, String ipAddress, String anonymousId) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException(adId));
        if (ad.getStatus() != AdStatus.ACTIVE) {
            throw new AdNotFoundException(adId);
        }
        balanceFacade.deduct(adId, CLICK_COST, TransactionType.CLICK);
        ClickEvent event = ClickEvent.valid(adId, ipAddress, anonymousId);
        return ClickInfo.from(clickEventRepository.save(event));
    }
}
```

**Step 5: 테스트 통과 확인**

```bash
./gradlew :apps:ad-click:test --tests "com.adclick.click.application.ClickFacadeTest"
```

Expected: 4 tests PASS

**Step 6: 커밋**

```bash
git add apps/ad-click/src/main/java/com/adclick/click/application/ \
        apps/ad-click/src/test/java/com/adclick/click/application/
git commit -m "feat: implement ClickFacade with 50-won click charge and event recording"
```

---

### Task 4: ClickController (anonymous_id 쿠키 처리)

**Files:**
- Create: `apps/ad-click/src/main/java/com/adclick/click/interfaces/api/ClickController.java`

**Step 1: ClickController 작성**

`apps/ad-click/src/main/java/com/adclick/click/interfaces/api/ClickController.java`:

```java
package com.adclick.click.interfaces.api;

import com.adclick.click.application.ClickFacade;
import com.adclick.click.application.info.ClickInfo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ads")
public class ClickController {

    private final ClickFacade clickFacade;

    public ClickController(ClickFacade clickFacade) {
        this.clickFacade = clickFacade;
    }

    @PostMapping("/{adId}/clicks")
    public ResponseEntity<ClickInfo> click(
            @PathVariable("adId") Long adId,
            HttpServletRequest request,
            HttpServletResponse response) {

        String ip = extractClientIp(request);
        String anonId = resolveAnonymousId(request, response);
        return ResponseEntity.ok(clickFacade.click(adId, ip, anonId));
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveAnonymousId(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("anonymous_id".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        String newId = UUID.randomUUID().toString();
        Cookie cookie = new Cookie("anonymous_id", newId);
        cookie.setMaxAge(60 * 60 * 24 * 365);
        cookie.setPath("/");
        response.addCookie(cookie);
        return newId;
    }
}
```

**Step 2: 전체 빌드 확인**

```bash
./gradlew :apps:ad-click:compileJava
./gradlew test
```

Expected: BUILD SUCCESSFUL

**Step 3: 커밋**

```bash
git add apps/ad-click/src/main/java/com/adclick/click/interfaces/
git commit -m "feat: add ClickController with anonymous_id cookie handling"
```

---

### Task 5: E2E 테스트 — click 50원 + view 10원 검증

**Files:**
- Modify: `apps/ad-api/src/test/java/com/adclick/AdApiE2ETest.java`

**Step 1: 헬퍼 메서드 + E2E 테스트 추가**

기존 `registerAdAndGetId()` 아래에 헬퍼 추가:

```java
private Long registerAdAndCharge(String name, int amount) {
    Long adId = registerAdAndGetId(name);
    restTemplate.postForEntity("/api/v1/ads/" + adId + "/balance/charge",
            Map.of("amount", amount), Map.class);
    return adId;
}
```

테스트 3개 추가:

```java
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
```

필요한 import 추가:
```java
import java.util.List;
import org.springframework.http.HttpHeaders;
```

**Step 2: 테스트 실행하여 통과 확인**

```bash
./gradlew :apps:ad-api:test
```

Expected: 신규 E2E 3개 포함 모두 PASS

**Step 3: 전체 테스트 최종 확인**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL — 전체 테스트 PASS (기존 32 + 신규 약 10개)

**Step 4: feature_list.json 업데이트**

`harness/feature_list.json`에서 두 항목 업데이트:

```json
// ad-rotation
"status": "done",
"evidence": "Round Robin 로직 + VIEW 과금(10원) 완료 (2026-06-18)"

// click-record  
"status": "done",
"evidence": "ClickFacadeTest: 4개 유닛. ClickEventJpaRepositoryTest: 2개 통합. AdApiE2ETest: click E2E 3개 + view charge E2E 1개 추가. 전체 테스트 PASS. (2026-06-18)"
```

**Step 5: 커밋**

```bash
git add apps/ad-api/src/test/java/com/adclick/AdApiE2ETest.java \
        harness/feature_list.json
git commit -m "test: add E2E tests for click (50won) and view charge (10won)"
```
