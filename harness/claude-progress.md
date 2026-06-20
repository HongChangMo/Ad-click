# Agent Progress Log — Ad Click Aggregation

> Legacy filename: `harness/claude-progress.md`.
> From 2026-06-19 onward, this is the all-agent progress log.
> Codex is the primary implementation agent; Claude is a secondary
> planning/review assistant unless the user explicitly changes that role.

## Current Verified State

| 항목 | 내용 |
|------|------|
| Repository root | `/Users/zzangmo/project/AdClick` |
| Standard startup path | `./gradlew :apps:ad-api:bootRun` (DB/Valkey 연결 필요) |
| Standard verification path | `./gradlew test` |
| Highest priority unfinished feature | 없음 — `harness/feature_list.json` 기준 MVP 1 priority 1-10 완료 |
| Current blocker | 없음 — ad-crud, balance-charge, ad-rotation, click-record, balance-concurrency, ad-exhausted, abuse-guard, click-stats, valkey-fallback, reconciliation-batch 완성 |

---

## Session Records

---

### Session 015 — 2026-06-20

**Goal**
PR merge 전 known issue cleanup — stale dependency test, infrastructure test gap, stale docs wording 정리

**Completed**
- `DependencyDirectionTest`의 검사 대상 클래스를 현재 `ClickFacade` 이름으로 갱신.
- `AdJpaRepositoryTest`에 `findAllIdsByStatus()`, `findRandomActive()` 통합 테스트 추가.
- 비-harness 설계 문서의 오래된 "클릭 10원" 표현을 VIEW=10원 / CLICK=50원 정책에 맞게 수정.
- setup 문서의 stale `ClickFacadeService` 예시를 `ClickFacade`로 갱신.
- 현재 handoff의 known issue 목록에서 해결된 항목 제거.

**Verification run**
```
./gradlew :apps:ad-management:test → BUILD SUCCESSFUL
./gradlew test → BUILD SUCCESSFUL
  - AdFacadeTest: 3 PASSED
  - BalanceFacadeTest: 14 PASSED
  - AdRotationFacadeTest: 7 PASSED
  - ClickFacadeTest: 8 PASSED
  - ClickReconciliationFacadeTest: 2 PASSED
  - AdJpaRepositoryTest: 4 PASSED
  - AdBalanceJpaRepositoryTest: 2 PASSED
  - ClickEventJpaRepositoryTest: 5 PASSED
  - ValKeyRotationAdapterTest: 5 PASSED
  - ValKeyAbuseGuardAdapterTest: 3 PASSED
  - AdApiE2ETest: 19 PASSED
  - AdApiValkeyFallbackE2ETest: 3 PASSED
  - AdClickApplicationTest: 1 PASSED
  - DependencyDirectionTest: 1 PASSED
  전체 77개 PASS
```

**Known issues / Lessons**
- 테스트 `ddl-auto=create` 전환으로 Hibernate drop 종료 경고는 제거됨.
- Redis/Lettuce reconnect cancellation warning은 일부 종료 시 남을 수 있지만 Gradle 결과는 BUILD SUCCESSFUL.

**Next best action**
PR #4 리뷰/머지 준비 또는 사용자와 MVP 2 범위 확정.

---

### Session 014 — 2026-06-20

**Goal**
reconciliation-batch (priority 10) — Valkey 장애 구간 중복 클릭 사후 탐지/환불

**Completed**
- `BalanceFacade.refund(adId, amount)` 추가.
  - 잔액 증가.
  - `balance_transactions`에 `TransactionType.REFUND` 기록.
- `ClickReconciliationFacade` 추가.
  - 지정한 `from`/`to` 구간의 valid click events 조회.
  - 동일 `adId + ipAddress` 그룹에서 첫 클릭은 유지하고 이후 클릭을 `DUPLICATE_IP`로 무효화.
  - 무효화된 클릭 1건당 50원 환불.
- `POST /api/v1/clicks/reconciliation` API 추가.
- `ClickEvent.markInvalid(...)`, reconciliation scan용 repository 메서드 추가.
- Valkey unavailable E2E에서 fail-open으로 중복 유효 클릭이 생긴 뒤 reconciliation으로 무효화/환불되는 흐름 검증.

**Verification run**
```
./gradlew :apps:ad-management:test :apps:ad-click:test → BUILD SUCCESSFUL
./gradlew :apps:ad-api:test --tests "com.adclick.AdApiValkeyFallbackE2ETest" → BUILD SUCCESSFUL
./gradlew test → BUILD SUCCESSFUL
  - AdFacadeTest: 3 PASSED
  - BalanceFacadeTest: 14 PASSED
  - AdRotationFacadeTest: 7 PASSED
  - ClickFacadeTest: 8 PASSED
  - ClickReconciliationFacadeTest: 2 PASSED
  - AdJpaRepositoryTest: 1 PASSED
  - AdBalanceJpaRepositoryTest: 2 PASSED
  - ClickEventJpaRepositoryTest: 5 PASSED
  - ValKeyRotationAdapterTest: 5 PASSED
  - ValKeyAbuseGuardAdapterTest: 3 PASSED
  - AdApiE2ETest: 19 PASSED
  - AdApiValkeyFallbackE2ETest: 3 PASSED
  - AdClickApplicationTest: 1 PASSED
  - DependencyDirectionTest: 1 PASSED
  전체 74개 PASS
```

**Evidence recorded**
- feature_list.json: reconciliation-batch status → done

**Known issues / Lessons**
- Testcontainers 종료 시 MySQL/Redis connection shutdown warning이 출력되지만 Gradle 결과는 BUILD SUCCESSFUL.
- 현재 reconciliation은 API-triggered use case이며, 2단계에서는 Kafka Consumer/전용 batch runner로 대체 가능.

**Next best action**
MVP 1 feature list 기준 priority 1-10 완료. 다음 작업은 사용자와 새 feature/cleanup 범위 확정 필요.

---

### Session 013 — 2026-06-20

**Goal**
valkey-fallback (priority 9) — Valkey 장애 시 클릭 fail-open + rotation DB fallback 검증

**Completed**
- `AdApiValkeyFallbackE2ETest` 추가.
  - Redis 컨테이너 없이 `spring.data.redis.port=1`로 Valkey 장애 상황 구성.
  - 클릭 요청이 rate limiter/abuse guard Valkey 장애에도 200으로 처리되는지 검증.
  - `/api/v1/ads/next`가 rotation queue Valkey 장애 시 DB ACTIVE 광고로 fallback되는지 검증.
- 기존 구현 확인:
  - `ClickRateLimiter`는 Valkey RuntimeException 시 `true` 반환.
  - `ValKeyAbuseGuardAdapter`는 Valkey RuntimeException 시 `Optional.empty()` 반환.
  - `AdRotationFacade`는 queuePort 장애 시 `nextAdFromDb()`로 fallback.

**Verification run**
```
./gradlew :apps:ad-api:test --tests "com.adclick.AdApiValkeyFallbackE2ETest" → BUILD SUCCESSFUL
./gradlew test → BUILD SUCCESSFUL
  - AdFacadeTest: 3 PASSED
  - BalanceFacadeTest: 12 PASSED
  - AdRotationFacadeTest: 7 PASSED
  - ClickFacadeTest: 8 PASSED
  - AdJpaRepositoryTest: 1 PASSED
  - AdBalanceJpaRepositoryTest: 2 PASSED
  - ClickEventJpaRepositoryTest: 4 PASSED
  - ValKeyRotationAdapterTest: 5 PASSED
  - ValKeyAbuseGuardAdapterTest: 3 PASSED
  - AdApiE2ETest: 19 PASSED
  - AdApiValkeyFallbackE2ETest: 2 PASSED
  - AdClickApplicationTest: 1 PASSED
  - DependencyDirectionTest: 1 PASSED
  전체 68개 PASS
```

**Evidence recorded**
- feature_list.json: valkey-fallback status → done

**Known issues / Lessons**
- Testcontainers 종료 시 MySQL/Redis connection shutdown warning이 출력되지만 Gradle 결과는 BUILD SUCCESSFUL.
- Circuit Breaker/Resilience4j는 아직 적용하지 않았고, 현재는 요구된 1단계 fail-open/fallback 동작 검증 완료 상태.

**Next best action**
`reconciliation-batch` (priority 10): Valkey 장애 구간 중복 클릭 사후 탐지/환불.

---

### Session 012 — 2026-06-20

**Goal**
click-stats (priority 8) — 기간별 유효/무효 클릭 수 조회 API

**Completed**
- `ClickStatsInfo` 응답 record 추가.
- `GET /api/v1/ads/{adId}/clicks/stats` API 추가.
  - `from`, `to` ISO DATE_TIME 쿼리 파라미터 지원.
  - 생략 시 전체 기간으로 집계.
- `ClickFacade.stats()` 추가.
  - 광고 존재 여부 확인 후 없으면 `AdNotFoundException`.
  - valid/invalid 클릭 수를 각각 조회.
- `ClickEventRepository.countByAdIdAndValidityBetween(...)` 포트 추가.
- `ClickEventJpaRepository.countByAdIdAndIsValidAndClickedAtBetween(...)` 추가.
- 테스트 편의를 위해 `ClickEvent.validAt(...)`, `ClickEvent.invalidAt(...)` factory 추가.

**Verification run**
```
./gradlew :apps:ad-click:test → BUILD SUCCESSFUL
./gradlew :apps:ad-api:test --tests "com.adclick.AdApiE2ETest" → BUILD SUCCESSFUL
./gradlew test → BUILD SUCCESSFUL
  - AdFacadeTest: 3 PASSED
  - BalanceFacadeTest: 12 PASSED
  - AdRotationFacadeTest: 7 PASSED
  - ClickFacadeTest: 8 PASSED
  - AdJpaRepositoryTest: 1 PASSED
  - AdBalanceJpaRepositoryTest: 2 PASSED
  - ClickEventJpaRepositoryTest: 4 PASSED
  - ValKeyRotationAdapterTest: 5 PASSED
  - ValKeyAbuseGuardAdapterTest: 3 PASSED
  - AdApiE2ETest: 19 PASSED
  - AdClickApplicationTest: 1 PASSED
  - DependencyDirectionTest: 1 PASSED
  전체 66개 PASS
```

**Evidence recorded**
- feature_list.json: click-stats status → done

**Known issues / Lessons**
- Testcontainers 종료 시 MySQL/Redis connection shutdown warning이 출력되지만 Gradle 결과는 BUILD SUCCESSFUL.

**Next best action**
`valkey-fallback` (priority 9): Valkey 장애 시 클릭 fail-open 및 rotation DB fallback 검증/보강.

---

### Session 011 — 2026-06-20

**Goal**
abuse-guard (priority 7) — Valkey TTL 기반 중복 클릭 방어 + API rate limit

**Completed**
- `AbuseGuardPort`와 `InvalidClickReason` 추가.
- `ValKeyAbuseGuardAdapter` 구현:
  - `abuse:ip:{ip}:{adId}` TTL 키로 동일 IP + 광고 60초 중복 클릭 감지.
  - `abuse:anon:{anonymousId}:{adId}` TTL 키로 동일 anonymous_id + 광고 60초 중복 클릭 감지.
  - Valkey 장애 시 fail-open으로 클릭 처리를 계속 진행.
- `ClickFacade.click()`이 중복 클릭이면 `ClickEvent.invalid(...)`를 저장하고 잔액 차감을 생략하도록 변경.
- `ClickInfo` 응답에 `invalidReason` 추가.
- `ClickRateLimiter` 추가:
  - `rate:click:ip:{ip}` Valkey INCR + TTL counter 기반.
  - 기본값 100 requests / 60 seconds.
  - 초과 시 `ClickController`가 HTTP 429 반환.
- 기존 잔액 동시성 E2E는 어뷰징 방어와 충돌하지 않도록 각 요청에 고유 IP/anonymous_id를 부여.

**Verification run**
```
./gradlew :apps:ad-click:test → BUILD SUCCESSFUL
./gradlew :apps:ad-api:test --tests "com.adclick.AdApiE2ETest" → BUILD SUCCESSFUL
./gradlew test → BUILD SUCCESSFUL
  - AdFacadeTest: 3 PASSED
  - BalanceFacadeTest: 12 PASSED
  - AdRotationFacadeTest: 7 PASSED
  - ClickFacadeTest: 6 PASSED
  - AdJpaRepositoryTest: 1 PASSED
  - AdBalanceJpaRepositoryTest: 2 PASSED
  - ClickEventJpaRepositoryTest: 3 PASSED
  - ValKeyRotationAdapterTest: 5 PASSED
  - ValKeyAbuseGuardAdapterTest: 3 PASSED
  - AdApiE2ETest: 17 PASSED
  - AdClickApplicationTest: 1 PASSED
  - DependencyDirectionTest: 1 PASSED
  전체 61개 PASS
```

**Evidence recorded**
- feature_list.json: abuse-guard status → done

**Known issues / Lessons**
- API rate limit은 Bucket4j 의존성을 추가하지 않고 Valkey counter로 구현했다. 현재 요구사항인 429 동작은 충족한다.
- Testcontainers 종료 시 MySQL/Redis connection shutdown warning이 출력되지만 Gradle 결과는 BUILD SUCCESSFUL.
- `DependencyDirectionTest`의 stale `ClickFacadeService` 클래스명은 아직 정리 필요.

**Next best action**
`click-stats` (priority 8): 기간별 유효/무효 클릭 수 조회 API.

---

### Session 010 — 2026-06-20

**Goal**
ad-exhausted (priority 6) — 잔액 0 도달 시 EXHAUSTED 전환 + rotation queue 제외

**Completed**
- `AdRotationQueuePort.remove(adId)` 추가.
- `ValKeyRotationAdapter.remove()` 구현: Redis List에서 해당 adId 전체 제거.
- `BalanceFacade.deduct()`가 잔액 0 도달 시 ACTIVE 광고를 EXHAUSTED로 전환.
- Valkey queue 제거는 DB commit 이후 `TransactionSynchronization.afterCommit`에서 실행.
- `BalanceFacadeTest`에 잔액 0 도달 시 EXHAUSTED 전환 및 queue remove 검증 추가.
- `ValKeyRotationAdapterTest`에 remove 통합 테스트 추가.
- `AdApiE2ETest.ad_becomes_exhausted_and_is_removed_from_rotation_when_balance_reaches_zero` 추가.
  - VIEW로 queue에 들어간 광고가 CLICK으로 잔액 0 도달.
  - 상태 EXHAUSTED 확인.
  - 추가 클릭 404 확인.
  - 이후 `/api/v1/ads/next`에서 해당 광고가 반환되지 않음 확인.
- `AdRotationFacadeTest`에 stale EXHAUSTED queue id를 반환/재삽입하지 않는 방어 테스트 추가.

**Verification run**
```
./gradlew :apps:ad-management:test --tests "com.adclick.management.application.BalanceFacadeTest" → BUILD SUCCESSFUL
./gradlew :apps:ad-management:test --tests "com.adclick.management.infrastructure.ValKeyRotationAdapterTest" → BUILD SUCCESSFUL
./gradlew :apps:ad-api:test --tests "com.adclick.AdApiE2ETest.ad_becomes_exhausted_and_is_removed_from_rotation_when_balance_reaches_zero" → BUILD SUCCESSFUL
./gradlew :apps:ad-management:test --tests "com.adclick.management.application.AdRotationFacadeTest" → BUILD SUCCESSFUL
./gradlew test → BUILD SUCCESSFUL
  - AdFacadeTest: 3 PASSED
  - BalanceFacadeTest: 12 PASSED
  - AdRotationFacadeTest: 7 PASSED
  - ClickFacadeTest: 4 PASSED
  - AdJpaRepositoryTest: 1 PASSED
  - AdBalanceJpaRepositoryTest: 2 PASSED
  - ClickEventJpaRepositoryTest: 2 PASSED
  - ValKeyRotationAdapterTest: 5 PASSED
  - AdApiE2ETest: 14 PASSED
  - AdClickApplicationTest: 1 PASSED
  - DependencyDirectionTest: 1 PASSED
  전체 52개 PASS
```

**Evidence recorded**
- feature_list.json: ad-exhausted status → done

**Known issues / Lessons**
- Testcontainers 종료 시 MySQL/Redis connection shutdown warning이 출력되지만 Gradle 결과는 BUILD SUCCESSFUL.
- `BalanceFacade.charge()`의 EXHAUSTED → ACTIVE 시 queue offer는 아직 트랜잭션 내부 호출이다. 현재 테스트는 통과하지만, 향후 outbox/after-commit 정리 후보.
- `DependencyDirectionTest`의 stale `ClickFacadeService` 클래스명은 아직 정리 필요.

**Next best action**
`abuse-guard` (priority 7): Valkey TTL 기반 중복 클릭 방어 + rate limit.

---

### Session 009 — 2026-06-20

**Goal**
balance-concurrency (priority 5) — SELECT FOR UPDATE + 음수 잔액 방어

**Completed**
- `AdBalance.subtract()` 음수 방어 가드 추가.
- `InsufficientBalanceException` 추가.
- `ApiExceptionHandler` 추가: 잔액 부족을 HTTP 404로 매핑.
- `AdBalanceRepository.findByAdIdForUpdate()` 포트 추가.
- `AdBalanceJpaRepository.findByAdIdForUpdate()`에 `@Lock(PESSIMISTIC_WRITE)` + JPQL 적용.
- `AdBalanceRepositoryAdapter`에 lock 조회 위임 추가.
- `BalanceFacade.deduct()`가 lock 조회 후 차감하도록 변경.
- `BalanceFacadeTest`에 잔액 부족/잔액 row 없음 테스트 추가.
- `AdApiE2ETest.concurrent_clicks_deduct_only_available_balance_and_never_go_negative` 추가.
  - 잔액 500원 광고에 동시 클릭 20개 요청.
  - 10개 성공, 10개 404, 최종 잔액 0 검증.
- Rotation E2E가 다른 ACTIVE 광고의 잔액 부족 상태와 충돌하지 않도록 테스트 데이터를 격리.

**Verification run**
```
./gradlew :apps:ad-management:test --tests "com.adclick.management.application.BalanceFacadeTest" → BUILD SUCCESSFUL
./gradlew :apps:ad-click:test --tests "com.adclick.click.application.ClickFacadeTest" → BUILD SUCCESSFUL
./gradlew :apps:ad-api:test --tests "com.adclick.AdApiE2ETest.concurrent_clicks_deduct_only_available_balance_and_never_go_negative" → BUILD SUCCESSFUL
./gradlew :apps:ad-api:test --tests "com.adclick.AdApiE2ETest" → BUILD SUCCESSFUL
./gradlew test → BUILD SUCCESSFUL
  - AdFacadeTest: 3 PASSED
  - BalanceFacadeTest: 11 PASSED
  - AdRotationFacadeTest: 6 PASSED
  - ClickFacadeTest: 4 PASSED
  - AdJpaRepositoryTest: 1 PASSED
  - AdBalanceJpaRepositoryTest: 2 PASSED
  - ClickEventJpaRepositoryTest: 2 PASSED
  - ValKeyRotationAdapterTest: 4 PASSED
  - AdApiE2ETest: 13 PASSED
  - AdClickApplicationTest: 1 PASSED
  - DependencyDirectionTest: 1 PASSED
  전체 48개 PASS
```

**Evidence recorded**
- feature_list.json: balance-concurrency status → done

**Known issues / Lessons**
- Testcontainers 종료 시 MySQL/Redis connection shutdown warning이 출력되지만 Gradle 결과는 BUILD SUCCESSFUL.
- 잔액 부족은 상태 전환 없이 404만 반환한다. EXHAUSTED 자동 전환은 priority 6 범위.
- `DependencyDirectionTest`의 stale `ClickFacadeService` 클래스명은 아직 정리 필요.

**Next best action**
`ad-exhausted` (priority 6): 잔액 0 도달 시 EXHAUSTED 전환 + rotation queue 제외.

---

### Session 008 — 2026-06-19

**Goal**
Codex takeover: make Codex the primary implementation agent and tighten harness
management rules.

**Completed**
- Added `AGENTS.md` as the canonical all-agent operating guide.
- Reframed `CLAUDE.md` as a legacy Claude entrypoint that points to `AGENTS.md`.
- Corrected the stale `ClickFacadeServiceTest` command to `ClickFacadeTest`.
- Clarified that `harness/claude-progress.md` is now an all-agent progress log
  despite the legacy filename.
- Tightened clean-state checklist language so partial features cannot be marked
  `done`.
- Updated `harness/feature_list.json` metadata date without changing feature
  statuses.

**Verification run**
No code changes. Tests not run.

**Evidence recorded**
No feature status changed.

**Known issues / Lessons**
- Some non-harness design/blog/diagram documents still contain older "click
  costs 10 won" wording. Current harness and `AGENTS.md` define VIEW=10 and
  CLICK=50.
- `DependencyDirectionTest` checks a stale `ClickFacadeService` class name; the
  dependency-direction intent remains valid, but the test should be modernized
  during the next code/test cleanup.

**Next best action**
`balance-concurrency` (priority 5): SELECT FOR UPDATE + negative-balance guard.

---

### Session 007 — 2026-06-19

**Goal**
click-record (priority 4) — POST /api/v1/ads/{adId}/clicks: 50원 차감 + 클릭 이벤트 기록 + VIEW 과금 소급 수정

**Completed**
- `BalanceFacade.deduct()` scope-creep 수정 (clamping + EXHAUSTED 자동 전환 제거 — priority 6 범위)
- `AdBalance.subtract()` 가드 제거 (priority 5에서 SELECT FOR UPDATE와 함께 처리)
- Task 0: `AdRotationFacade` VIEW 과금(10원) 소급 추가, `BalanceFacade.deduct(adId, 10, VIEW)` 호출
- Task 1: `ClickEvent` 엔티티 + `ClickEventRepository` 도메인 인터페이스 (ad-click 모듈 첫 Java)
- Task 2: `ClickEventJpaRepository` + `ClickEventRepositoryAdapter` + `TestApplication` + 통합 테스트 2개
- Task 3: `ClickFacade.click()` (50원 차감, ACTIVE 체크, 클릭 이벤트 저장) + `ClickInfo` + 유닛 테스트 4개
- Task 4: `ClickController` — POST /api/v1/ads/{adId}/clicks, X-Forwarded-For, anonymous_id 쿠키 처리 (HttpOnly)
- Task 5: E2E 테스트 3개 (50원 차감, PAUSED 404, 쿠키 발급) + feature_list.json 업데이트

**Verification run**
```
./gradlew test → BUILD SUCCESSFUL
  - AdFacadeTest: 3 PASSED
  - BalanceFacadeTest: 9 PASSED (deduct_view, deduct_click 추가)
  - AdRotationFacadeTest: 6 PASSED (view charge 검증 추가)
  - ClickFacadeTest: 4 PASSED (Unit)
  - AdJpaRepositoryTest: 1 PASSED
  - AdBalanceJpaRepositoryTest: 2 PASSED
  - ClickEventJpaRepositoryTest: 2 PASSED
  - ValKeyRotationAdapterTest: 4 PASSED
  - AdApiE2ETest: 12 PASSED (기존 9 + click E2E 3 신규)
  - AdClickApplicationTest: 1 PASSED
  - DependencyDirectionTest: 1 PASSED
  전체 45개 PASS
```

**Evidence recorded**
- feature_list.json: ad-rotation status → done, click-record status → done

**Known issues / Lessons**
- `BalanceFacade.deduct()` 호출 전 ACTIVE 광고라도 잔액이 0일 수 있음 → priority 5에서 SELECT FOR UPDATE + 잔액 체크 필요
- `AdNotFoundException`을 PAUSED/EXHAUSTED 케이스에도 사용 → HTTP 404 정확, 로그 메시지 혼동 가능 (priority 6 이전에 개선)
- `ClickFacade`가 `AdRepository`를 직접 주입 (ad-management 의존) — 승인된 의존 방향이므로 문제 없음

**Next best action**
`balance-concurrency` (priority 5): SELECT FOR UPDATE + 음수 잔액 방어

---

### Session 006 — 2026-06-18

**Goal**
ad-rotation (priority 3) — Round Robin 광고 균등 노출 구현

**Completed**
- `AdRotationQueuePort` 인터페이스 (domain): `offer`, `poll`, `tryRebuildLock`, `releaseRebuildLock`
- `NoActiveAdException` (application): ACTIVE 광고 없을 때 404
- `AdRotationFacade` (application): LPOP poll → 빈 큐 시 SETNX 재구성 → RPUSH round robin, Valkey 장애 DB fallback
- `AdRotationController` (interfaces): GET /api/v1/ads/next
- `ValKeyRotationAdapter` (infrastructure): StringRedisTemplate 기반 LPOP/RPUSH/SETNX
- `AdRepository` 메서드 추가: `findAllActiveIds()`, `findRandomActive()`
- `BalanceFacade` 수정: EXHAUSTED → ACTIVE 전환 시 `queuePort.offer(adId)` 추가
- 테스트: `AdRotationFacadeTest` (5 unit) + `ValKeyRotationAdapterTest` (4 integration) + E2E 2개 추가
- PR #2 생성 및 머지 완료

**Verification run**
```
./gradlew test → BUILD SUCCESSFUL
  - AdFacadeTest: 3 PASSED
  - BalanceFacadeTest: 7 PASSED
  - AdRotationFacadeTest: 5 PASSED
  - AdJpaRepositoryTest: 1 PASSED
  - AdBalanceJpaRepositoryTest: 2 PASSED
  - ValKeyRotationAdapterTest: 4 PASSED
  - AdApiE2ETest: 8 PASSED
  - AdClickApplicationTest: 1 PASSED
  - DependencyDirectionTest: 1 PASSED
  전체 32개 PASS
```

**Evidence recorded**
- feature_list.json: ad-rotation status → done

**Known issues / Lessons**
- `@Param("status")` 누락 시 `-parameters` 플래그 의존 → 명시적 `@Param` 필수
- Lock-loss path는 `nextAdFromQueue()` → `Optional.empty()` → `nextAdFromDb()` fallback 흐름으로 처리해야 함
- `BalanceFacade` 내 `queuePort.offer()` 가 `@Transactional` 내부에서 호출되어 롤백 시 불일치 가능 → priority 6 (ad-exhausted) 구현 시 `@TransactionalEventListener(AFTER_COMMIT)` 적용 예정

**Next best action**
`click-record` (priority 4): POST /api/v1/ads/{adId}/clicks — 잔액 10원 차감, 클릭 이벤트 기록, anonymous_id 쿠키 처리

---

### Session 005 — 2026-06-16

**Goal**
Circuit Breaker 설계 이해 및 설계 문서 보완

**Completed**
- Circuit Breaker(Resilience4j)가 이 서비스에서 하는 역할 정리
  - Valkey 장애 시 타임아웃 낭비 없이 즉시 Fallback 전환
  - CLOSED → OPEN → HALF-OPEN → CLOSED 상태 전환
- Fallback 기본값 설계 확정
  - `isAbuser()` → `false` (Fail Open)
  - `getNextAdId()` → DB ACTIVE 광고 랜덤 조회
  - `deductBalance()` → DB Pessimistic Lock
- `docs/plans/2026-06-09-ad-click-aggregation-design.md` Section 5.5에 Circuit Breaker 서브섹션 추가
  - 컴포넌트별 Fallback 반환값 표
  - `@CircuitBreaker` + `fallbackMethod` 코드 예시
  - 설계 원칙: 가용성 > 정확성 (장애 구간에만 허용)

**Verification run**
코드 변경 없음 — 테스트 재실행 불필요

**Evidence recorded**
없음 (설계 문서 변경)

**Known issues / Lessons**
없음

**Next best action**
`ad-rotation` (priority 3) 구현:
- `ValKeyRotationAdapter` — LPOP/RPUSH/SETNX 래핑
- `AdRotationFacade` — `getNextAd()`: LPOP → Round Robin, 큐 빔 감지 시 SETNX 재구성
- `AdRotationController` — GET /api/v1/ads/next
- `BalanceFacade.charge()` 완성: EXHAUSTED → ACTIVE 전환 시 Valkey 큐에 RPUSH

---

### Session 003 — 2026-06-10

**Goal**
ad-crud (priority 1) — 광고 등록 및 상태 관리 API 구현

**Completed**
- `Ad` 엔티티, `AdStatus` enum (ACTIVE/PAUSED/EXHAUSTED) — domain 계층
- `AdJpaRepository` — infrastructure 계층 (Spring Data JPA)
- `AdService` — application 계층 (register, changeStatus, getAd)
- `AdNotFoundException` — `@ResponseStatus(NOT_FOUND)` 처리
- `AdController` — interfaces 계층 (POST /api/v1/ads, PATCH /{adId}/status, GET /{adId})
- DTO: `AdRegisterRequest`, `AdRegisterResponse`, `AdStatusChangeRequest` (record)
- Testcontainers 의존성 추가 (ad-management, ad-api 모듈)
- `ad-api`에 `spring-boot-starter-web` 명시적 추가 (`@PathVariable` 컴파일 classpath 필요)
- `AdClickApplicationTest` Testcontainers 방식으로 전환 (JPA/Redis 컴포넌트 추가로 기존 exclusion 방식 불가)
- `TestApplication.java` 추가 (ad-management 통합 테스트용 `@SpringBootApplication`)
- 테스트 3종: `AdServiceTest`(Unit), `AdJpaRepositoryTest`(Integration), `AdApiE2ETest`(E2E)

**Verification run**
```
./gradlew test → BUILD SUCCESSFUL
  - AdServiceTest: 3 PASSED
  - AdJpaRepositoryTest: 1 PASSED (MySQL Testcontainer)
  - AdApiE2ETest: 3 PASSED (MySQL + Redis Testcontainer)
  - AdClickApplicationTest: 1 PASSED
  - DependencyDirectionTest: 1 PASSED
```

**Evidence recorded**
- feature_list.json: ad-crud status → done

**Known issues / Lessons**
- Spring Boot 3.5 + Gradle → `@PathVariable`에 명시적 이름 필요 (`-parameters` 플래그 없을 때)
- `@DataJpaTest` + `Replace.NONE` 시 `ddl-auto` 기본값 없음 → `@TestPropertySource`로 명시 필요
- 멀티모듈에서 `@DataJpaTest` 사용 시 `@SpringBootConfiguration`이 없으면 실패 → test 소스에 `TestApplication` 추가

**Next best action**
`balance-charge` (priority 2) 구현:
- `AdBalance` 엔티티, `BalanceTransaction` 엔티티 추가
- `BalanceService`: POST /api/v1/ads/{adId}/balance/charge
- EXHAUSTED → ACTIVE 자동 전환 로직

---

### Session 002 — 2026-06-09

**Goal**
Gradle 멀티모듈 프로젝트 뼈대 구성

**Completed**
- Git 초기화 및 `.gitignore` 설정
- Gradle 8.14.5 wrapper 설정 (Spring Initializr 부트스트랩 활용)
- 루트 `build.gradle` 작성 (Spring Boot 3.5.0, Java 21 Toolchain)
- `apps/ad-management` 모듈: build.gradle + 4계층 패키지 구조 (interfaces, application, domain, infrastructure)
- `apps/ad-click` 모듈: build.gradle + 4계층 패키지 구조 + `project(':apps:ad-management')` 의존
- `apps/ad-api` 모듈: build.gradle + AdClickApplication.java + application.yml + contextLoads 테스트
- 의존 방향 검증 테스트: `DependencyDirectionTest` (ad-management → ad-click 참조 불가 확인)

**Verification run**
```
./gradlew build → BUILD SUCCESSFUL
./gradlew :apps:ad-api:test → contextLoads PASSED
./gradlew :apps:ad-management:test → DependencyDirectionTest PASSED
```

**Evidence recorded**
- 모든 모듈 `BUILD SUCCESSFUL`
- `contextLoads` PASSED (DB/Redis autoconfigure 제외)
- `DependencyDirectionTest` PASSED (의존 방향 단방향 확인)

**Commits**
- `chore: init repository`
- `chore: configure gradle multi-module root`
- `chore: add ad-management module`
- `chore: add ad-click module`
- `chore: add ad-api module with Spring Boot application`
- `test: verify ad-management does not depend on ad-click`

**Known risks**
- Spring Boot 3.3.5 → 3.5.0으로 변경 (3.3.5 EOL, Initializr 지원 종료). 설계 문서 버전 표기 업데이트 필요
- Gradle 8.14.5의 `io.spring.dependency-management` 플러그인 deprecation warning (Gradle 9.0 호환성) — 현재 동작에는 무관
- Java 21 컴파일: 시스템 기본 Java는 17 (Corretto) → Gradle Toolchain으로 Corretto 21 자동 선택
- `junit-platform-launcher` 명시적 추가 필요 (Spring Boot 3.5.0 + Gradle 조합)

**Next best action**
`ad-crud` (priority 1) 구현 시작:
- `feature_list.json`의 `ad-crud` status → `in_progress`로 변경
- `apps/ad-management` 모듈에 Ad 엔티티, 리포지토리, 서비스, 컨트롤러 구현
- POST /api/v1/ads, PATCH /api/v1/ads/{adId}/status 엔드포인트 구현

---

### Session 001 — 2026-06-09

**Goal**
시스템 설계 및 설계 문서 작성

**Completed**
- 요구사항 정의 (R1~R6)
- 기술 스택 확정: Java/Spring Boot, MySQL, Valkey, Bucket4j
- 전체 아키텍처 설계 (단계별 확장 전략 포함)
- 데이터 모델 설계 (ads, ad_balances, click_events, balance_transactions, outbox_events)
- 클릭 처리 상세 흐름 설계 ([0] 쿠키 처리 ~ [7] 상태 업데이트)
- 핵심 설계 결정 6가지 문서화 (균등 노출, 어뷰징 방어, 동시성, Outbox, Valkey Fallback, 잔액 충전)
- 멀티모듈 구조 확정 (apps/ad-api, apps/ad-management, apps/ad-click)
- 레이어드 아키텍처 확정 (interfaces, application, domain, infrastructure)
- 시스템 핵심 구간 및 구현 우선순위 문서화
- harness 디렉토리 초기화 (feature_list.json, claude-progress.md, session-handoff.md, clean-state-checklist.md)

**Verification run**
없음 (설계 단계)

**Evidence recorded**
없음 (설계 단계)

**Commits**
없음

**Known risks**
- 멀티모듈 Gradle 설정 시 모듈 간 의존성 순환 주의
- ad-click → ad-management 단방향 의존 유지 필요
- Valkey Round Robin Queue 초기화 시 SETNX 락 TTL 설정 값 결정 필요

**Next best action**
`ad-crud` (priority 1) 구현 시작:
- `feature_list.json`의 `ad-crud` status → `in_progress`로 변경
- `apps/ad-management` 모듈에 Ad 엔티티, 리포지토리, 서비스, 컨트롤러 구현
- POST /api/v1/ads, PATCH /api/v1/ads/{adId}/status 엔드포인트 구현
