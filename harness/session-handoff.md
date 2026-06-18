# Session Handoff — Ad Click Aggregation

> 이 파일은 각 세션 종료 시 업데이트합니다. 새 세션은 이 파일을 가장 먼저 읽으세요.

---

## Last Updated: 2026-06-16 (Session 005)

---

## Currently Verified

- `./gradlew test` → BUILD SUCCESSFUL (전체 16개 테스트 PASS)
  - `AdFacadeTest` (3) — Unit
  - `BalanceFacadeTest` (7) — Unit
  - `AdJpaRepositoryTest` (1) — Integration (MySQL Testcontainer)
  - `AdBalanceJpaRepositoryTest` (2) — Integration (MySQL Testcontainer)
  - `AdApiE2ETest` (6) — E2E (MySQL + Redis Testcontainer)
  - `AdClickApplicationTest` (1) — Context load
  - `DependencyDirectionTest` (1) — 의존 방향 단방향 확인
- `ad-crud` feature: **done**
- `balance-charge` feature: **done**

---

## Changes This Session

코드 변경 없음 — 설계 문서 보완만 진행.

- `docs/plans/2026-06-09-ad-click-aggregation-design.md`
  - Section 5.5에 `#### Circuit Breaker 동작 방식 및 Fallback 기본값 (2단계 이후)` 서브섹션 추가
  - 컴포넌트별 Fallback 반환값 표: `isAbuser()` → `false`, `getNextAdId()` → DB 랜덤, `deductBalance()` → DB Pessimistic Lock
  - Circuit Breaker 상태 전환 흐름 (CLOSED → OPEN → HALF-OPEN → CLOSED) 다이어그램
  - Resilience4j `@CircuitBreaker` + `fallbackMethod` 코드 예시

---

## Package Structure (현재 구현 완료된 구조)

```
com.adclick.management/
  interfaces/api/
    AdController.java
    BalanceController.java
    dto/: AdRegisterRequest, AdStatusChangeRequest, BalanceChargeRequest
  application/
    AdFacade.java, BalanceFacade.java
    AdNotFoundException.java
    info/: AdInfo, BalanceInfo
  domain/
    Ad.java, AdStatus.java, AdRepository.java
    AdBalance.java, AdBalanceRepository.java
    BalanceTransaction.java, BalanceTransactionRepository.java
    TransactionType.java
  infrastructure/
    AdJpaRepository.java, AdRepositoryAdapter.java
    AdBalanceJpaRepository.java, AdBalanceRepositoryAdapter.java
    BalanceTransactionJpaRepository.java, BalanceTransactionRepositoryAdapter.java
```

---

## Still Broken or Unverified

- `bootRun` 미검증 (로컬 DB/Valkey 연결 필요)
- `ad-rotation` ~ `reconciliation-batch` 모든 feature `not_started`

---

## Next Best Action

**`ad-rotation` (priority 3) 구현 시작**

구현 대상:
- `AdRotationService` — application: `getNextAd()` 메서드
  - Valkey LPOP으로 다음 광고 ID 가져오기
  - RPUSH로 큐 끝에 다시 추가 (Round Robin)
  - 큐 비어있으면 SETNX 분산 락으로 단일 재구성
  - Valkey 장애 시 DB Fallback (ACTIVE 광고 랜덤)
- `AdRotationController` — interfaces: GET /api/v1/ads/next
- `BalanceFacade.charge()` 완성: EXHAUSTED → ACTIVE 전환 시 Valkey 큐에 RPUSH
- `ValKeyRotationAdapter` — infrastructure: Valkey LPOP/RPUSH/SETNX 래핑

**건드리지 말아야 할 것**
- 설계 문서, 기존 테스트

---

## Commands

```bash
# 전체 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :apps:ad-management:test
./gradlew :apps:ad-api:test

# 특정 테스트 클래스
./gradlew :apps:ad-management:test --tests "com.adclick.management.application.BalanceFacadeTest"
./gradlew :apps:ad-api:test --tests "com.adclick.AdApiE2ETest"
```
