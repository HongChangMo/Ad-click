# Session Handoff — Ad Click Aggregation

> 이 파일은 각 세션 종료 시 업데이트합니다. 새 세션은 이 파일을 가장 먼저 읽으세요.
> Canonical agent rules live in `AGENTS.md`.
> From 2026-06-19 onward, Codex is the primary implementation agent.

---

## Last Updated: 2026-06-20 (Session 018)

---

## Currently Verified

- `./gradlew test` → BUILD SUCCESSFUL (전체 81개 테스트 PASS)
  - `AdFacadeTest` (3) — Unit
  - `BalanceFacadeTest` (14) — Unit
  - `AdRotationFacadeTest` (7) — Unit
  - `ClickFacadeTest` (8) — Unit
  - `ClickReconciliationFacadeTest` (2) — Unit
  - `ValKeyRotationAdapterCircuitBreakerTest` (2) — Unit
  - `ValKeyAbuseGuardAdapterCircuitBreakerTest` (1) — Unit
  - `ClickRateLimiterTest` (1) — Unit
  - `AdJpaRepositoryTest` (4) — Integration (MySQL Testcontainer)
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
- `valkey-circuit-breaker` feature: **done**
- MVP 1 feature list priority 1-10: **done**
- MVP 2 feature list priority 11: **done**
- Agent ownership: **Codex primary**, Claude secondary planning/review assistant
- Local bootRun: **verified** with Docker Compose MySQL + Valkey-compatible Redis
- Local seed data: **verified** (`docs/schema.sql` + `docs/seed-mvp1.sql`)

---

## Changes This Session (Session 018)

- PR #6 `MVP 1 로컬 실행 및 운영 문서 보강` 머지 확인.
- MVP 2 첫 작업 `valkey-circuit-breaker` (priority 11) 구현 완료.
- Resilience4j `resilience4j-retry:2.3.0`, `resilience4j-circuitbreaker:2.3.0` 의존성을 `ad-management`, `ad-click` 모듈에 추가.
- Valkey 호출에 짧은 지수 백오프 retry 적용.
  - 기본 `maxAttempts=2`, `initialInterval=50ms`, `multiplier=2.0`, `maxInterval=200ms`.
- Valkey Round Robin queue 경로에 circuit breaker 적용.
  - `offer`, `remove`, `poll`, `tryRebuildLock`, `releaseRebuildLock` 보호.
  - `poll` 장애/OPEN 시 `Optional.empty()` fallback.
  - `tryRebuildLock` 장애/OPEN 시 `false` fallback.
- Valkey abuse guard 경로에 circuit breaker 적용.
  - 장애/OPEN 시 기존 fail-open 정책대로 `Optional.empty()` 반환.
- 클릭 rate limiter 경로에 circuit breaker 적용.
  - 장애/OPEN 시 기존 fail-open 정책대로 `true` 반환.
- `application.yml`에 circuit breaker 기본 설정 명시.
- `docs/operations/mvp1-runbook.md`의 circuit breaker 상태 설명 갱신.
- bean name 충돌 해결: `managementValkeyCircuitBreaker`, `clickValkeyCircuitBreaker`로 명시.
- 추가 테스트:
  - `ValKeyRotationAdapterCircuitBreakerTest` (2)
  - `ValKeyAbuseGuardAdapterCircuitBreakerTest` (1)
  - `ClickRateLimiterTest` (1)
- 검증:
  - `./gradlew :apps:ad-management:test :apps:ad-click:test` → BUILD SUCCESSFUL
  - `./gradlew :apps:ad-api:test` → BUILD SUCCESSFUL
  - `./gradlew test` → BUILD SUCCESSFUL (전체 81개 PASS)

## Changes Previous Session (Session 017)

- MVP 1 운영성 보강 진행.
- `README.md` 추가: 로컬 실행, 테스트, 주요 API curl 예시 정리.
- `docker-compose.yml` 추가: MySQL 8.0 + Redis 7.2 기반 Valkey 호환 캐시 로컬 의존성 구성.
- `docs/schema.sql` 추가: `ddl-auto=validate`용 로컬 MySQL 스키마.
- `docs/seed-mvp1.sql` 추가: 로컬 검증용 광고 50개 seed 데이터.
  - ACTIVE 40개, PAUSED 5개, EXHAUSTED 5개.
  - 36-40번 ACTIVE 광고는 잔액 소진 검증용 저잔액 광고.
- `docs/operations/mvp1-runbook.md` 추가: 정상 과금 플로우, 어뷰징 방어, Valkey 장애 보정 절차 정리.
- Docker Compose 의존성 healthy 확인.
- `docs/schema.sql`, `docs/seed-mvp1.sql`을 실제 MySQL에 적용해 검증.
- `./gradlew :apps:ad-api:bootRun` 실제 기동 검증.
- 로컬 HTTP smoke 검증:
  - `GET /api/v1/ads/1`
  - `GET /api/v1/ads/1/balance`
  - `GET /api/v1/ads/next`
  - `POST /api/v1/ads/1/clicks`
- `./gradlew test` 재확인 결과 BUILD SUCCESSFUL (문서/SQL 변경만 있어 테스트 task는 up-to-date).

## Changes Previous Session (Session 016)

- PR #4 `클릭 과금 안정성 및 통계/보정 기능 구현` 머지 확인.
- 로컬 `main`을 `origin/main`으로 fast-forward 동기화.
- 후속 handoff 갱신용 브랜치 `chore/post-pr4-handoff` 생성.
- `./gradlew clean test` 재실행 결과 BUILD SUCCESSFUL.
  - 테스트 종료 시 Hibernate drop DDL / MySQL `Communications link failure` 경고는 재현되지 않음.
  - Spring/Tomcat/Hikari 정상 shutdown 로그와 JVM bootstrap classpath warning은 남아 있음.

## Changes Previous Session (Session 015)

- `DependencyDirectionTest`의 stale `ClickFacadeService` 클래스명을 현재 `ClickFacade`로 갱신.
- `AdJpaRepositoryTest`에 `findAllIdsByStatus()`, `findRandomActive()` 통합 테스트 추가.
- 비-harness 설계 문서의 오래된 "클릭 10원" 표현을 VIEW=10원 / CLICK=50원 정책에 맞게 수정.
- setup 문서의 stale `ClickFacadeService` 예시를 `ClickFacade`로 갱신.
- 해결된 known issue를 현재 handoff에서 제거.

## Changes Previous Session (Session 014)

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

- 인증/권한은 아직 없음.
- 스키마 마이그레이션 도구는 아직 없음. 로컬 MVP 1 실행은 `docs/schema.sql`로 준비.
- reconciliation은 HTTP 수동 트리거 방식. 전용 batch runner는 MVP 2 후보.
- Resilience4j는 programmatic Retry + CircuitBreaker만 도입됨. Spring Boot Actuator/Prometheus metric 노출은 후속 운영성 후보.
- 테스트 `ddl-auto=create` 전환으로 Hibernate drop 종료 경고는 제거됨.
- Redis/Lettuce reconnect cancellation warning은 일부 종료 시 남을 수 있지만 테스트 결과는 BUILD SUCCESSFUL.

---

## 요구사항 (확정)

- **조회(GET /api/v1/ads/next)**: 10원 차감 (VIEW 트랜잭션)
- **클릭(POST /api/v1/ads/{adId}/clicks)**: 50원 차감 (CLICK 트랜잭션)
- `TransactionType`: `CHARGE` | `VIEW` | `CLICK` | `REFUND`

## Next Best Action

**MVP 1 feature list 기준 priority 1-10 완료.**
**MVP 2 priority 11 완료.**
- Valkey circuit breaker PR 리뷰/머지.
- 이후 MVP 2 다음 후보 선택: reconciliation batch runner 분리, outbox/retry, 관리자 통계/모니터링 API 중 택1.

**건드리지 말아야 할 것**
- 명시되지 않은 priority 12+ 후속 기능: outbox/retry 등 미적용

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
