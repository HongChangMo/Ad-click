# Session Handoff — Ad Click Aggregation

> 이 파일은 각 세션 종료 시 업데이트합니다. 새 세션은 이 파일을 가장 먼저 읽으세요.

---

## Last Updated: 2026-06-10 (Session 003)

---

## Currently Verified

- `./gradlew test` → BUILD SUCCESSFUL (전체 9개 테스트 PASS)
  - `AdServiceTest` (3) — Unit
  - `AdJpaRepositoryTest` (1) — Integration (MySQL Testcontainer)
  - `AdApiE2ETest` (3) — E2E (MySQL + Redis Testcontainer)
  - `AdClickApplicationTest` (1) — Context load
  - `DependencyDirectionTest` (1) — 의존 방향 단방향 확인
- `ad-crud` feature: **done**

---

## Changes This Session

- `apps/ad-management/src/main/java/com/adclick/management/`
  - `domain/Ad.java`, `domain/AdStatus.java`
  - `infrastructure/AdJpaRepository.java`
  - `application/AdService.java`, `application/AdNotFoundException.java`
  - `interfaces/AdController.java`, DTOs (AdRegisterRequest, AdRegisterResponse, AdStatusChangeRequest)
- `apps/ad-management/build.gradle` — Testcontainers 의존성 추가
- `apps/ad-api/build.gradle` — `spring-boot-starter-web` 명시 추가, Testcontainers 추가
- `apps/ad-api/src/test/AdClickApplicationTest.java` — Testcontainers 방식으로 전환
- `apps/ad-management/src/test/TestApplication.java` — DataJpaTest용 SpringBootConfiguration
- `apps/ad-management/src/test/.../AdServiceTest.java` — Unit test
- `apps/ad-management/src/test/.../AdJpaRepositoryTest.java` — Integration test
- `apps/ad-api/src/test/AdApiE2ETest.java` — E2E test

---

## Still Broken or Unverified

- `bootRun` 미검증 (로컬 DB/Valkey 연결 필요)
- `balance-charge` ~ `reconciliation-batch` 모든 feature `not_started`

---

## Next Best Action

**`balance-charge` (priority 2) 구현 시작**

구현 대상:
- `AdBalance` 엔티티 (`ad_balances` 테이블) — domain
- `BalanceTransaction` 엔티티 (`balance_transactions` 테이블) — domain
- `AdBalanceJpaRepository`, `BalanceTransactionJpaRepository` — infrastructure
- `BalanceService` — application: `charge(adId, amount)` 메서드
  - 잔액 증가 + balance_transactions INSERT (type=CHARGE)
  - EXHAUSTED → ACTIVE 자동 전환 (Valkey 큐 재진입은 다음 단계)
- `BalanceController` — interfaces: POST /api/v1/ads/{adId}/balance/charge, GET /api/v1/ads/{adId}/balance

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
./gradlew :apps:ad-management:test --tests "com.adclick.management.application.AdServiceTest"
./gradlew :apps:ad-api:test --tests "com.adclick.AdApiE2ETest"
```
