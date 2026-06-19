# Claude Progress Log — Ad Click Aggregation

## Current Verified State

| 항목 | 내용 |
|------|------|
| Repository root | `/Users/zzangmo/project/AdClick` |
| Standard startup path | `./gradlew :apps:ad-api:bootRun` (DB/Valkey 연결 필요) |
| Standard verification path | `./gradlew test` |
| Highest priority unfinished feature | `balance-concurrency` (priority 5) — SELECT FOR UPDATE + 음수 방어 |
| Current blocker | 없음 — ad-crud, balance-charge, ad-rotation, click-record 완성 |

---

## Session Records

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
