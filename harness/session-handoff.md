# Session Handoff — Ad Click Aggregation

> 이 파일은 각 세션 종료 시 업데이트합니다. 새 세션은 이 파일을 가장 먼저 읽으세요.
> Canonical agent rules live in `AGENTS.md`.
> From 2026-06-19 onward, Codex is the primary implementation agent.

---

## Last Updated: 2026-06-20 (Session 014)

---

## Currently Verified

- `./gradlew test` → BUILD SUCCESSFUL (전체 74개 테스트 PASS)
  - `AdFacadeTest` (3) — Unit
  - `BalanceFacadeTest` (14) — Unit
  - `AdRotationFacadeTest` (7) — Unit
  - `ClickFacadeTest` (8) — Unit
  - `ClickReconciliationFacadeTest` (2) — Unit
  - `AdJpaRepositoryTest` (1) — Integration (MySQL Testcontainer)
  - `AdBalanceJpaRepositoryTest` (2) — Integration (MySQL Testcontainer)
  - `ClickEventJpaRepositoryTest` (5) — Integration (MySQL Testcontainer)
  - `ValKeyRotationAdapterTest` (5) — Integration (Redis:7.2 Testcontainer)
  - `ValKeyAbuseGuardAdapterTest` (3) — Integration (Redis:7.2 Testcontainer)
  - `AdApiE2ETest` (19) — E2E (MySQL + Redis Testcontainer)
  - `AdApiValkeyFallbackE2ETest` (3) — E2E (MySQL Testcontainer + unavailable Redis)
  - `AdClickApplicationTest` (1) — Context load
  - `DependencyDirectionTest` (1) — 의존 방향 단방향 확인
- `ad-crud` feature: **done**
- `balance-charge` feature: **done**
- `ad-rotation` feature: **done**
- `click-record` feature: **done**
- `balance-concurrency` feature: **done**
- `ad-exhausted` feature: **done**
- `abuse-guard` feature: **done**
- `click-stats` feature: **done**
- `valkey-fallback` feature: **done**
- `reconciliation-batch` feature: **done**
- MVP 1 feature list priority 1-10: **done**
- Agent ownership: **Codex primary**, Claude secondary planning/review assistant

---

## Changes This Session (Session 014)

- `reconciliation-batch` (priority 10) 구현 완료.
  - `BalanceFacade.refund(adId, amount)` 추가: 잔액 증가 + `REFUND` 거래 이력 기록.
  - `ClickReconciliationFacade` 추가: 장애 구간 valid click scan, 동일 `adId + ipAddress` 중복 탐지.
  - 중복 클릭은 `ClickEvent.markInvalid(DUPLICATE_IP)`로 무효화.
  - 중복 1건당 50원 환불.
  - `POST /api/v1/clicks/reconciliation` API 추가.
  - `ClickEventJpaRepository`에 reconciliation scan 쿼리 추가.
  - `AdApiValkeyFallbackE2ETest`에 fail-open 중복 클릭 → reconciliation 환불 E2E 추가.

## Changes Previous Session (Session 013)

- `valkey-fallback` (priority 9) 검증 완료.
  - `AdApiValkeyFallbackE2ETest` 추가.
  - Redis unavailable 설정(`127.0.0.1:1`, 200ms timeout)에서 클릭 요청 200 + 잔액 차감 검증.
  - Redis unavailable 설정에서 `/api/v1/ads/next`가 DB ACTIVE 광고를 반환하고 VIEW 차감하는지 검증.
  - 기존 `ClickRateLimiter`, `ValKeyAbuseGuardAdapter`, `AdRotationFacade` fallback 구현에 대한 E2E 증거 확보.

## Changes Previous Session (Session 012)

- `click-stats` (priority 8) 구현 완료.
  - `ClickStatsInfo` 응답 record 추가.
  - `ClickFacade.stats(adId, from, to)` 추가.
  - `ClickEventRepository.countByAdIdAndValidityBetween(...)` 포트 추가.
  - `ClickEventJpaRepository.countByAdIdAndIsValidAndClickedAtBetween(...)` 추가.
  - `ClickController`에 `GET /api/v1/ads/{adId}/clicks/stats` 추가.
  - `from`, `to` ISO DATE_TIME 쿼리 파라미터를 지원하며 생략 시 전체 기간 조회.
  - `ClickFacadeTest`, `ClickEventJpaRepositoryTest`, `AdApiE2ETest`에 검증 추가.

## Changes Previous Session (Session 011)

- `abuse-guard` (priority 7) 구현 완료.
  - `AbuseGuardPort`, `InvalidClickReason` 추가.
  - `ValKeyAbuseGuardAdapter`가 60초 TTL 키로 동일 IP/anonymous_id + 광고 중복 클릭을 감지.
  - 중복 클릭은 `ClickEvent.invalid(...)`로 저장하고 `BalanceFacade.deduct()`를 호출하지 않음.
  - `ClickInfo`에 `invalidReason` 응답 필드 추가.
  - `ClickRateLimiter`가 Valkey INCR + TTL counter로 IP별 기본 100회/60초 제한 적용.
  - `ClickController`가 rate limit 초과 시 HTTP 429 반환.
  - `ClickFacadeTest`, `ClickEventJpaRepositoryTest`, `ValKeyAbuseGuardAdapterTest`, `AdApiE2ETest`에 검증 추가.

## Changes Previous Session (Session 010)

- `ad-exhausted` (priority 6) 구현 완료.
  - `AdRotationQueuePort.remove(adId)` 포트 추가.
  - `ValKeyRotationAdapter.remove()`가 Redis List에서 해당 adId를 모두 제거하도록 구현.
  - `BalanceFacade.deduct()`가 잔액 0 도달 시 ACTIVE 광고를 EXHAUSTED로 전환.
  - Valkey queue 제거는 `TransactionSynchronization.afterCommit`에서 실행.
  - `BalanceFacadeTest`에 잔액 0 도달 시 EXHAUSTED + queue remove 검증 추가.
  - `ValKeyRotationAdapterTest`에 remove 통합 테스트 추가.
  - `AdApiE2ETest`에 EXHAUSTED 전환, 클릭 404, rotation 제외 검증 추가.
  - `AdRotationFacadeTest`에 stale EXHAUSTED queue id를 반환/재삽입하지 않는 방어 테스트 추가.

## Changes Previous Session (Session 009)

- `balance-concurrency` (priority 5) 구현 완료.
  - `AdBalance.subtract()`에 음수 방어 가드 추가.
  - `InsufficientBalanceException` 추가.
  - `ApiExceptionHandler`에서 잔액 부족을 HTTP 404로 매핑.
  - `AdBalanceRepository.findByAdIdForUpdate()` 포트 추가.
  - `AdBalanceJpaRepository.findByAdIdForUpdate()`에 `PESSIMISTIC_WRITE` 적용.
  - `BalanceFacade.deduct()`가 `SELECT FOR UPDATE` 경로로 잔액을 조회하도록 변경.
  - `BalanceFacadeTest`에 잔액 부족/잔액 row 없음 테스트 추가.
  - `AdApiE2ETest`에 동시 클릭 20개 중 10개 성공/10개 404/잔액 0 검증 추가.
  - Rotation E2E는 잔액 없는 ACTIVE 광고와 충돌하지 않도록 테스트 데이터를 격리.

## Changes Previous Session (Session 008)

- Codex takeover documentation completed.
  - Added `AGENTS.md` as the canonical all-agent operating guide.
  - Reframed `CLAUDE.md` as a legacy Claude entrypoint.
  - Corrected stale `ClickFacadeServiceTest` command to `ClickFacadeTest`.
  - Clarified that `harness/claude-progress.md` is an all-agent progress log
    despite the legacy filename.
  - Tightened `harness/clean-state-checklist.md` wording around `done`
    evidence discipline.
  - Updated `harness/feature_list.json` metadata date without changing feature
    statuses.

## Changes Previous Session (Session 007)

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
    AdController.java, BalanceController.java, AdRotationController.java, ApiExceptionHandler.java
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
    InsufficientBalanceException.java
    AdRotationQueuePort.java
  infrastructure/
    AdJpaRepository.java, AdRepositoryAdapter.java
    AdBalanceJpaRepository.java, AdBalanceRepositoryAdapter.java
    BalanceTransactionJpaRepository.java, BalanceTransactionRepositoryAdapter.java
    ValKeyRotationAdapter.java

com.adclick.click/
  interfaces/api/
    ClickController.java  ← POST /api/v1/ads/{adId}/clicks
    ClickReconciliationController.java
    ClickRateLimiter.java
    dto/: ReconciliationRequest.java
  application/
    ClickFacade.java, ClickReconciliationFacade.java
    info/: ClickInfo.java, ClickStatsInfo.java, ReconciliationInfo.java
  domain/
    AbuseGuardPort.java, ClickEvent.java, ClickEventRepository.java, InvalidClickReason.java
  infrastructure/
    ClickEventJpaRepository.java, ClickEventRepositoryAdapter.java, ValKeyAbuseGuardAdapter.java
```

---

## Still Broken or Unverified

- `bootRun` 미검증 (로컬 DB/Valkey 연결 필요)
- `AdJpaRepositoryTest` gap: `findAllActiveIds()`, `findRandomActive()` 통합 테스트 미작성
  - E2E로 검증되어 있으나 infrastructure 계층 테스트 부재
- 일부 비-harness 설계/블로그/다이어그램 문서에 과거 "클릭 10원" 표현이 남아 있음
  - 현재 확정 요구사항은 VIEW=10원, CLICK=50원
- `DependencyDirectionTest`가 stale `ClickFacadeService` 클래스명을 확인함
  - 의존 방향 검증 의도는 유효하나 다음 코드/테스트 정리 때 현재 클래스명 기준으로 갱신 필요
- Testcontainers 종료 시 MySQL/Redis connection shutdown warning이 출력되지만 테스트 결과는 BUILD SUCCESSFUL

---

## 요구사항 (확정)

- **조회(GET /api/v1/ads/next)**: 10원 차감 (VIEW 트랜잭션)
- **클릭(POST /api/v1/ads/{adId}/clicks)**: 50원 차감 (CLICK 트랜잭션)
- `TransactionType`: `CHARGE` | `VIEW` | `CLICK` | `REFUND`

## Next Best Action

**MVP 1 feature list 기준 priority 1-10 완료.**
- 다음 작업은 사용자와 새 feature 또는 cleanup 범위를 확정한 뒤 진행.

**건드리지 말아야 할 것**
- 명시되지 않은 priority 11+ 후속 기능: outbox/retry 등 미적용

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
