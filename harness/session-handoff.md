# Session Handoff — Ad Click Aggregation

> 이 파일은 각 세션 종료 시 업데이트합니다. 새 세션은 이 파일을 가장 먼저 읽으세요.
> Canonical agent rules live in `AGENTS.md`.
> From 2026-06-19 onward, Codex is the primary implementation agent.

---

## Last Updated: 2026-06-20 (Session 030)

---

## Currently Verified

- `./gradlew test` → BUILD SUCCESSFUL (전체 108개 테스트 PASS)
- `./gradlew :apps:ad-click:test :apps:ad-api:test :apps:ad-aggregation:test` → BUILD SUCCESSFUL (Session 022)
  - `AdFacadeTest` (3) — Unit
  - `BalanceFacadeTest` (14) — Unit
  - `AdRotationFacadeTest` (7) — Unit
  - `ClickFacadeTest` (8) — Unit
  - `ClickReconciliationFacadeTest` (2) — Unit
  - `ClickReconciliationRunnerTest` (1) — Unit
  - `ScheduledClickReconciliationJobTest` (2) — Unit
  - `ValKeyReconciliationLockAdapterTest` (4) — Unit
  - `ValKeyRotationAdapterCircuitBreakerTest` (2) — Unit
  - `ValKeyAbuseGuardAdapterCircuitBreakerTest` (1) — Unit
  - `ClickRateLimiterTest` (1) — Unit
  - `KafkaClickEventPublisherTest` (1) — Unit
  - `OutboxClickEventPublisherTest` (1) — Unit
  - `ClickEventOutboxRelayTest` (3) — Unit
  - `ClickEventOutboxClaimServiceTest` (4) — Unit
  - `ClickEventOutboxAdminServiceTest` (3) — Unit
  - `ClickEventOutboxAdminControllerTest` (2) — Unit
  - `ClickEventAggregationConsumerTest` (2) — Unit
  - `ClickAggregationServiceTest` (2) — Integration (MySQL Testcontainer)
  - `ClickEventAggregationKafkaIntegrationTest` (1) — Integration (Embedded Kafka + MySQL Testcontainer)
  - `ClickEventAggregationConsumerDltIntegrationTest` (1) — Integration (Embedded Kafka + MySQL Testcontainer)
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
- `reconciliation-runner` feature: **done**
- `reconciliation-lock` feature: **done**
- `kafka-click-aggregation-foundation` feature: **done**
- `kafka-click-outbox-relay` feature: **done**
- `kafka-click-aggregation-integration-test` feature: **done**
- `technology-responsibility-local-doc-ignore` feature: **done**
- `kafka-config-yml-split` feature: **done**
- `kafka-batch-outbox-and-consumer` feature: **done**
- `outbox-claim-retry-backoff` feature: **done**
- `outbox-dlq-consumer-failure-policy` feature: **done**
- `outbox-failed-retry-api` feature: **done**
- `kafka-consumer-dlt-runbook` feature: **done**
- MVP 1 feature list priority 1-10: **done**
- MVP 2 feature list priority 11: **done**
- MVP 2 feature list priority 12: **done**
- MVP 2 feature list priority 13: **done**
- MVP 2 feature list priority 14: **done**
- MVP 2 feature list priority 15: **done**
- MVP 2 feature list priority 16: **done**
- MVP 2 feature list priority 17: **done**
- MVP 2 feature list priority 18: **done**
- MVP 2 feature list priority 19: **done**
- MVP 2 feature list priority 20: **done**
- Agent ownership: **Codex primary**, Claude secondary planning/review assistant
- Local bootRun: **verified** with Docker Compose MySQL + Valkey-compatible Redis
- Local seed data: **verified** (`docs/schema.sql` + `docs/seed-mvp1.sql`)

---

## Changes This Session (Session 030)

- PR #17 `FAILED Outbox 재처리 API 추가` merge 완료.
- Consumer-side DLT 설정 추가.
  - `KafkaConsumerDltConfig` 추가.
  - `DefaultErrorHandler`와 `DeadLetterPublishingRecoverer` 사용.
  - `adclick.kafka.topics.click-events-dlt` 추가.
  - `adclick.kafka.consumer.dlt.retry-interval-ms`, `max-attempts` 추가.
- Embedded Kafka 기반 DLT 통합 테스트 추가.
  - consumer batch 처리 실패 후 retry 소진 시 DLT topic으로 메시지가 발행되는지 검증.
- 로컬 실행 및 장애 점검 가이드 추가.
  - `docs/operations/local-run-guide.md`
  - Kafka UI, outbox FAILED, consumer DLT, topic 사전 생성 주의사항 정리.
- README/runbook 갱신.
- 검증:
  - `./gradlew :apps:ad-aggregation:test --tests "com.adclick.aggregation.application.ClickEventAggregationConsumerDltIntegrationTest"` → BUILD SUCCESSFUL
  - `./gradlew :apps:ad-api:test` → BUILD SUCCESSFUL
  - `./gradlew test` → BUILD SUCCESSFUL (전체 108개 PASS)
  - `harness/feature_list.json` JSON parse OK
  - `git diff --check` PASS

## Changes Previous Session (Session 029)

- PR #16 `Outbox DLQ와 Consumer 실패 정책 추가` merge 완료.
- `FAILED` outbox 운영 조회/재처리 API 추가.
  - `GET /api/v1/admin/click-event-outbox/failed?size=20`
  - `POST /api/v1/admin/click-event-outbox/{outboxId}/retry`
- `ClickEventOutboxAdminService` 추가.
  - `FAILED` row 목록 조회.
  - 특정 `FAILED` row를 `PENDING`으로 전환해 relay 재발행 대상에 포함.
  - 없는 id 또는 `FAILED`가 아닌 row는 empty 반환, API는 404 반환.
- README/runbook/harness 갱신.
- 검증:
  - `./gradlew :apps:ad-click:test` → BUILD SUCCESSFUL
  - `./gradlew :apps:ad-api:test` → BUILD SUCCESSFUL
  - `./gradlew test` → BUILD SUCCESSFUL (전체 107개 PASS, test tasks up-to-date)
  - `harness/feature_list.json` JSON parse OK
  - `git diff --check` PASS

## Changes Previous Session (Session 028)

- PR #15 `Outbox claim 분리와 retry backoff 추가` merge 완료.
- Outbox 영구 실패 격리 추가.
  - `ClickEventOutboxStatus.FAILED` 추가.
  - `click_event_outbox.failed_at` 추가.
  - retry 횟수가 `adclick.kafka.outbox.relay.retry.max-attempts` 이상이면 row를 `FAILED`로 격리.
  - `FAILED` row는 producer-side DLQ로 운영 점검한다.
- Kafka aggregation consumer 실패 정책 명시.
  - batch DB 처리 성공 시에만 manual ack.
  - 처리 실패 시 ack하지 않고 예외를 다시 던져 Kafka 재전달 대상 유지.
- README/runbook/schema/harness 갱신.
- 검증:
  - `./gradlew :apps:ad-click:test :apps:ad-aggregation:test` → BUILD SUCCESSFUL
  - `./gradlew :apps:ad-api:test` → BUILD SUCCESSFUL
  - `./gradlew test` → BUILD SUCCESSFUL (전체 102개 PASS, test tasks up-to-date)
  - `harness/feature_list.json` JSON parse OK
  - `git diff --check` PASS

## Changes Previous Session (Session 027)

- Outbox relay의 DB lock 유지 시간을 줄이기 위해 claim transaction 분리.
- `click_event_outbox` 필드 추가:
  - `claimed_by`
  - `claimed_at`
  - `next_retry_at`
- `ClickEventOutboxClaimService` 추가.
  - PENDING row를 짧은 트랜잭션에서 PROCESSING으로 claim.
  - stale PROCESSING row를 PENDING으로 복구.
  - publish 성공/실패 결과를 별도 트랜잭션으로 기록.
- `ClickEventOutboxRelay` 변경.
  - claim 이후 DB lock 없이 Kafka publish 수행.
  - 실패 시 지수 백오프 기반 `next_retry_at` 설정.
  - 시작 시 stale PROCESSING row 복구 수행.
- `application-kafka.yml`에 retry/processing-timeout 설정 추가.
- `docs/schema.sql`, README, runbook, harness 갱신.
- 추가 테스트:
  - `ClickEventOutboxClaimServiceTest` (3)
  - `ClickEventOutboxRelayTest` stale 복구 검증 추가.
- 검증:
  - `./gradlew :apps:ad-click:test` → BUILD SUCCESSFUL
  - `./gradlew :apps:ad-api:test` → BUILD SUCCESSFUL
  - `./gradlew test` → BUILD SUCCESSFUL (전체 100개 PASS, test tasks up-to-date)

## Changes Previous Session (Session 026)

- Kafka 대량 트래픽 대비 batch 처리 구현.
- Outbox relay 변경:
  - `PENDING` row를 설정된 `batch-size`만큼 조회.
  - PENDING row 조회에 `PESSIMISTIC_WRITE` lock 적용.
  - 조회 row를 `PROCESSING`으로 표시.
  - Kafka send를 먼저 일괄 요청한 뒤 결과별로 PUBLISHED/PENDING 상태 갱신.
  - 실패 시 `attempt_count`, `last_error` 갱신 후 PENDING으로 되돌려 retry 가능하게 처리.
- Kafka consumer 변경:
  - `@KafkaListener`가 `List<ClickEventMessage>` batch를 수신.
  - `ClickAggregationService.aggregateAll()`로 한 트랜잭션에서 batch 처리.
  - DB 처리 후 manual ack.
- `application-kafka.yml` batch 설정 추가:
  - producer `batch-size=32768`, `linger.ms=20`, `compression-type=lz4`.
  - consumer `max.poll.records=100`.
  - listener `type=batch`.
  - outbox relay `batch-size=100`.
- README/runbook에 batch 처리 설정과 운영 기준 반영.
- 검증:
  - `./gradlew :apps:ad-click:test :apps:ad-aggregation:test` → BUILD SUCCESSFUL
  - `./gradlew :apps:ad-api:test` → BUILD SUCCESSFUL
  - `./gradlew test` → BUILD SUCCESSFUL (전체 96개 PASS, test tasks up-to-date)

## Changes Previous Session (Session 025)

- Kafka 설정을 `apps/ad-api/src/main/resources/application-kafka.yml`로 분리.
  - `spring.kafka.*` producer/consumer/listener 설정 이동.
  - `adclick.kafka.*` topic/outbox relay 설정 이동.
  - `application.yml`에는 `spring.config.import=classpath:application-kafka.yml` 추가.
- README/runbook에 Kafka 설정 파일 위치 반영.
- 검증:
  - `./gradlew :apps:ad-api:test` → BUILD SUCCESSFUL

## Changes Previous Session (Session 024)

- PR #11 `Kafka 클릭 이벤트 Outbox relay 추가` squash merge 완료.
- `docs/architecture/technology-responsibilities.md`를 Git 추적에서 제거하고 로컬 전용 문서로 전환.
- `.gitignore`에 `docs/architecture/technology-responsibilities.md` 추가.
- README에서 해당 문서 링크 제거.
- README의 전체 테스트 수치를 현재 기준 96개로 갱신.

## Changes Previous Session (Session 023)

- 개발자 설명용 문서 `docs/architecture/technology-responsibilities.md` 추가.
  - Spring Boot API, MySQL, Valkey, Kafka, Kafka consumer idempotency, 보장 수준, 테스트 전략 정리.
  - Kafka outbox relay의 at-least-once 발행과 consumer white box idempotency의 역할을 명시.
- README 운영 문서 목록에 기술 책임 문서 링크 추가.
- `ad-aggregation` Kafka 통합 테스트 추가.
  - `ClickEventAggregationKafkaIntegrationTest`
  - Embedded Kafka topic `ad-click-events-integration` 사용.
  - MySQL Testcontainer로 `processed_click_events`, `click_daily_stats` 실제 DB 반영 검증.
  - 같은 `clickEventId` 중복 메시지는 한 번만 집계되고, valid/invalid count가 분리 집계되는지 확인.
- `ad-aggregation`에 `spring-boot-starter-json` 의존성 추가.
- 검증:
  - `./gradlew :apps:ad-aggregation:test` → BUILD SUCCESSFUL
  - `./gradlew test` → BUILD SUCCESSFUL (전체 96개 PASS)

## Changes Previous Session (Session 022)

- PR #10 `Kafka 클릭 이벤트 발행 및 멱등 집계 Consumer 추가` squash merge 완료.
- MVP 2 `kafka-click-outbox-relay` (priority 15) 구현 완료.
- 클릭 이벤트 Kafka 발행 경로를 afterCommit direct publish에서 outbox relay 방식으로 변경.
  - `ClickFacade`는 클릭 저장 트랜잭션 내부에서 `ClickEventPublisher` 포트를 호출한다.
  - `OutboxClickEventPublisher`가 `click_event_outbox` PENDING row를 저장한다.
  - `ClickEventOutboxRelay`가 PENDING row를 Kafka로 발행하고 성공 시 PUBLISHED로 전환한다.
  - Kafka 발행 실패 시 PENDING 유지, `attempt_count` 증가, `last_error` 기록.
- `KafkaClickEventPublisher`는 Kafka send adapter로 책임 축소.
- `application.yml`에 `adclick.kafka.outbox.relay.*` 설정 추가.
- `docs/schema.sql`에 `click_event_outbox` 테이블 추가.
- ad-api 통합 테스트에서 Kafka listener와 outbox relay를 비활성화해 외부 Kafka 없이 테스트가 안정적으로 종료되도록 조정.
- README/runbook/harness에 outbox relay 운영 기준 반영.
- 추가 테스트:
  - `OutboxClickEventPublisherTest` (1)
  - `ClickEventOutboxRelayTest` (2)
- 검증:
  - `./gradlew :apps:ad-click:test` → BUILD SUCCESSFUL
  - `./gradlew :apps:ad-click:test :apps:ad-api:test :apps:ad-aggregation:test` → BUILD SUCCESSFUL
  - `./gradlew test` → BUILD SUCCESSFUL (전체 95개 PASS, test tasks up-to-date)

## Changes Previous Session (Session 021)

- PR #9 `클릭 보정 Runner 중복 실행 방지` 머지 확인.
- MVP 2 `kafka-click-aggregation-foundation` (priority 14) 구현 완료.
- `apps:ad-aggregation` 모듈 추가.
  - Spring Kafka 기반 consumer 모듈.
  - `ClickEventMessage` 수신 후 DB 처리 성공 시 manual ack.
  - `processed_click_events.click_event_id` 기준 idempotency 처리.
  - `click_daily_stats` 일별 valid/invalid projection 업데이트.
- `ad-click`에 Kafka producer 추가.
  - `ClickEventPublisher` 포트 추가.
  - `KafkaClickEventPublisher` 구현 추가.
  - `ClickEventMessage` 추가.
- `ClickFacade`가 클릭 이벤트 저장 후 transaction commit 이후 Kafka publish 하도록 변경.
- Kafka publish 실패는 클릭 요청을 실패시키지 않고 fail-open 처리.
- `docker-compose.yml`에 Kafka와 Kafka UI 추가.
  - Kafka: `localhost:9092`
  - Kafka UI: `http://localhost:8081`
- `application.yml`에 Kafka producer/consumer JSON 직렬화 설정 추가.
  - producer idempotence: `enable.idempotence=true`, `acks=all`, `retries=Integer.MAX_VALUE`, `max.in.flight.requests.per.connection=5`.
  - consumer: `enable-auto-commit=false`, listener `ack-mode=manual`.
  - producer `max.block.ms=100`으로 브로커 부재 시 요청 지연 제한.
- README/runbook에 Kafka topic 및 Kafka UI 정보 추가.
- 추가 테스트:
  - `KafkaClickEventPublisherTest` (1)
  - `ClickEventAggregationConsumerTest` (1)
  - `ClickAggregationServiceTest` (2)
  - `ClickFacadeTest`에 afterCommit publish 검증 추가.
- 검증:
  - `./gradlew :apps:ad-click:test :apps:ad-aggregation:test` → BUILD SUCCESSFUL
  - `./gradlew :apps:ad-click:test :apps:ad-aggregation:test :apps:ad-api:test` → BUILD SUCCESSFUL
  - `./gradlew :apps:ad-api:test` → BUILD SUCCESSFUL
  - `./gradlew test` → BUILD SUCCESSFUL (전체 92개 PASS)

## Changes Previous Session (Session 020)

- MVP 2 `reconciliation-lock` (priority 13) 구현 완료.
- `ReconciliationLockPort` 추가.
- `ValKeyReconciliationLockAdapter` 추가.
  - key: `reconciliation:lock:{lockKey}`
  - scheduled job lock key: `scheduled`
  - 기본 TTL: 300초
  - Valkey 장애 시 보정 누락 방지를 위해 fail-open으로 `true` 반환.
- `ScheduledClickReconciliationJob`에 lock 획득/해제 적용.
  - lock 획득 실패 시 runner 실행 skip.
  - lock 획득 성공 시 runner 실행 후 finally에서 release.
- `application.yml`에 `lock-ttl-seconds` 설정 추가.
- README/runbook에 Valkey TTL lock 정책과 한계 반영.
- 추가 테스트:
  - `ScheduledClickReconciliationJobTest` lock 성공/skip 검증.
  - `ValKeyReconciliationLockAdapterTest` setIfAbsent 성공/실패, fail-open, release 검증.
- 검증:
  - `./gradlew :apps:ad-click:test` → BUILD SUCCESSFUL
  - `./gradlew :apps:ad-api:test` → BUILD SUCCESSFUL
  - `./gradlew test` → BUILD SUCCESSFUL (전체 88개 PASS, test tasks up-to-date)

## Changes Previous Session (Session 019)

- PR #7 `Valkey 경로 Retry 및 Circuit Breaker 도입` 머지 확인.
- MVP 2 `reconciliation-runner` (priority 12) 구현 완료.
- `ClickReconciliationRunner` 추가.
  - 수동 API와 스케줄 job이 함께 쓰는 공통 보정 진입점.
- `ScheduledClickReconciliationJob` 추가.
  - `adclick.click.reconciliation.runner.enabled=true`일 때만 등록.
  - `fixed-delay-ms`, `window-minutes`, `lag-seconds` 설정으로 최근 구간을 주기 보정.
- `ClickReconciliationController`가 facade 직접 호출 대신 runner를 호출하도록 변경.
- `AdClickApplication`에 `@EnableScheduling` 추가.
- 기본 설정은 스케줄 runner 비활성화(`enabled=false`).
- README/runbook에 runner 설정과 운영 주의점 추가.
- 추가 테스트:
  - `ClickReconciliationRunnerTest` (1)
  - `ScheduledClickReconciliationJobTest` (1)
- 검증:
  - `./gradlew :apps:ad-click:test` → BUILD SUCCESSFUL
  - `./gradlew :apps:ad-api:test` → BUILD SUCCESSFUL
  - `./gradlew test` → BUILD SUCCESSFUL (전체 83개 PASS, test tasks up-to-date)

## Changes Previous Session (Session 018)

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
- Reconciliation scheduler는 Valkey TTL lock으로 중복 실행을 줄인다. Valkey 장애 시 fail-open이므로 강한 exactly-once batch 보장은 아님.
- Kafka producer는 outbox relay 방식이다. 클릭 저장과 발행 예정 이벤트 저장은 같은 DB 트랜잭션으로 묶인다.
- relay는 at-least-once 발행 성격이므로 Kafka 중복 발행 가능성이 있다. 집계 consumer는 `processed_click_events.click_event_id`로 중복 집계를 방지한다.
- Kafka consumer는 DB idempotency로 중복 집계 반영을 방지하지만 Kafka+DB 원자 트랜잭션은 아님.
- 테스트 `ddl-auto=create` 전환으로 Hibernate drop 종료 경고는 제거됨.
- Redis/Lettuce reconnect cancellation warning은 일부 종료 시 남을 수 있지만 테스트 결과는 BUILD SUCCESSFUL.

---

## 요구사항 (확정)

- **조회(GET /api/v1/ads/next)**: 10원 차감 (VIEW 트랜잭션)
- **클릭(POST /api/v1/ads/{adId}/clicks)**: 50원 차감 (CLICK 트랜잭션)
- `TransactionType`: `CHARGE` | `VIEW` | `CLICK` | `REFUND`

## Next Best Action

**MVP 1 feature list 기준 priority 1-10 완료.**
**MVP 2 priority 11-14 완료.**
- Kafka click aggregation foundation PR 리뷰/머지.
- 이후 MVP 2 다음 후보 선택: Kafka 통합 테스트, 관리자 통계 API, outbox relay 병렬 처리/lock 보강 중 택1.

**건드리지 말아야 할 것**
- 명시되지 않은 priority 16+ 후속 기능: Kafka 통합 테스트, 관리자 통계 API 등 미적용

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
