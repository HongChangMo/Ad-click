# Session Handoff — Ad Click Aggregation

> 이 파일은 각 세션 종료 시 업데이트합니다. 새 세션은 이 파일을 가장 먼저 읽으세요.

---

## Last Updated: 2026-06-19 (Session 007)

---

## Currently Verified

- `./gradlew test` → BUILD SUCCESSFUL (전체 45개 테스트 PASS)
  - `AdFacadeTest` (3) — Unit
  - `BalanceFacadeTest` (9) — Unit
  - `AdRotationFacadeTest` (6) — Unit
  - `ClickFacadeTest` (4) — Unit
  - `AdJpaRepositoryTest` (1) — Integration (MySQL Testcontainer)
  - `AdBalanceJpaRepositoryTest` (2) — Integration (MySQL Testcontainer)
  - `ClickEventJpaRepositoryTest` (2) — Integration (MySQL Testcontainer)
  - `ValKeyRotationAdapterTest` (4) — Integration (Redis:7.2 Testcontainer)
  - `AdApiE2ETest` (12) — E2E (MySQL + Redis Testcontainer)
  - `AdClickApplicationTest` (1) — Context load
  - `DependencyDirectionTest` (1) — 의존 방향 단방향 확인
- `ad-crud` feature: **done**
- `balance-charge` feature: **done**
- `ad-rotation` feature: **done**
- `click-record` feature: **done**

---

## Changes This Session (Session 007)

- `BalanceFacade.deduct()` scope-creep 수정: clamping + EXHAUSTED 자동 전환 제거
- `AdBalance.subtract()` 음수 가드 제거 (priority 5에서 SELECT FOR UPDATE와 함께 처리)
- `TransactionType`: CHARGE | VIEW | CLICK | REFUND
- `AdRotationFacade.getNextAd()`: `@Transactional` + BalanceFacade 주입 + VIEW 10원 차감
- **ad-click 모듈 첫 Java 구현:**
  - `ClickEvent` 엔티티 (click_events 테이블)
  - `ClickEventRepository` 도메인 인터페이스
  - `ClickEventJpaRepository` + `ClickEventRepositoryAdapter`
  - `ClickFacade.click(adId, ip, anonId)` — ACTIVE 체크 + 50원 차감 + 클릭 기록
  - `ClickInfo` (Facade 반환 객체)
  - `ClickController` — POST /api/v1/ads/{adId}/clicks, X-Forwarded-For IP, anonymous_id 쿠키 (HttpOnly)
  - E2E 테스트 3개 추가

---

## Package Structure (현재 구현 완료된 구조)

```
com.adclick.management/
  interfaces/api/
    AdController.java, BalanceController.java, AdRotationController.java
    dto/: AdRegisterRequest, AdStatusChangeRequest, BalanceChargeRequest
  application/
    AdFacade.java, BalanceFacade.java, AdRotationFacade.java
    AdNotFoundException.java, NoActiveAdException.java
    info/: AdInfo, BalanceInfo
  domain/
    Ad.java, AdStatus.java, AdRepository.java
    AdBalance.java, AdBalanceRepository.java
    BalanceTransaction.java, BalanceTransactionRepository.java
    TransactionType.java (CHARGE | VIEW | CLICK | REFUND)
    AdRotationQueuePort.java
  infrastructure/
    AdJpaRepository.java, AdRepositoryAdapter.java
    AdBalanceJpaRepository.java, AdBalanceRepositoryAdapter.java
    BalanceTransactionJpaRepository.java, BalanceTransactionRepositoryAdapter.java
    ValKeyRotationAdapter.java

com.adclick.click/
  interfaces/api/
    ClickController.java  ← POST /api/v1/ads/{adId}/clicks
  application/
    ClickFacade.java
    info/: ClickInfo.java
  domain/
    ClickEvent.java, ClickEventRepository.java
  infrastructure/
    ClickEventJpaRepository.java, ClickEventRepositoryAdapter.java
```

---

## Still Broken or Unverified

- `bootRun` 미검증 (로컬 DB/Valkey 연결 필요)
- `AdJpaRepositoryTest` gap: `findAllActiveIds()`, `findRandomActive()` 통합 테스트 미작성
  - E2E로 검증되어 있으나 infrastructure 계층 테스트 부재
- 잔액 음수 방어 미적용 (priority 5): `AdBalance.subtract()` 가드 없음 → SELECT FOR UPDATE 도입 시 추가
- EXHAUSTED 자동 전환 미적용 (priority 6)
- 어뷰징 방어 미적용 (priority 7)

---

## 요구사항 (확정)

- **조회(GET /api/v1/ads/next)**: 10원 차감 (VIEW 트랜잭션)
- **클릭(POST /api/v1/ads/{adId}/clicks)**: 50원 차감 (CLICK 트랜잭션)
- `TransactionType`: `CHARGE` | `VIEW` | `CLICK` | `REFUND`

## Next Best Action

**`balance-concurrency` (priority 5) 구현:**
- `AdBalance.subtract()`: 음수 방어 가드 추가
- `BalanceFacade.deduct()`: `SELECT FOR UPDATE` (pessimistic lock)
- 동시성 테스트: `ExecutorService` 50~100 스레드 동시 클릭 → 잔액 검증

**건드리지 말아야 할 것**
- priority 6 (ad-exhausted): EXHAUSTED 자동 전환 미적용
- priority 7 (abuse-guard): Valkey TTL 어뷰징 체크 미적용

---

## Commands

```bash
# 전체 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :apps:ad-management:test
./gradlew :apps:ad-click:test
./gradlew :apps:ad-api:test

# 특정 테스트 클래스
./gradlew :apps:ad-click:test --tests "com.adclick.click.application.ClickFacadeTest"
./gradlew :apps:ad-api:test --tests "com.adclick.AdApiE2ETest"
```
