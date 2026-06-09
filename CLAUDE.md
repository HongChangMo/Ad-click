# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 세션 시작 시 필독

새 세션 시작 시 아래 순서로 읽으세요.

1. `harness/session-handoff.md` — 직전 세션 상태와 다음 행동
2. `harness/feature_list.json` — 기능 목록과 현재 status
3. `harness/claude-progress.md` — 전체 진행 이력

세션 종료 전 `harness/clean-state-checklist.md` 항목을 점검하세요.

상세 설계: `docs/plans/2026-06-09-ad-click-aggregation-design.md`

---

## 빌드 및 실행 명령

```bash
./gradlew build
./gradlew test
./gradlew :apps:ad-management:test
./gradlew :apps:ad-click:test
./gradlew :apps:ad-click:test --tests "com.adclick.click.application.ClickFacadeServiceTest"
./gradlew :apps:ad-api:bootRun
```

---

## 모듈 간 의존 방향 (단방향 엄수)

```
ad-api → ad-management
ad-api → ad-click
ad-click → ad-management
```

`ad-management`는 `ad-click`을 절대 import하지 않습니다.

---

## 핵심 불변 조건

1. 클릭 1회에 잔액이 정확히 10원만 차감된다.
2. 잔액은 0 미만으로 내려가지 않는다.
3. EXHAUSTED 광고에 클릭 요청 시 404를 반환한다.
4. 동일 IP 또는 익명 ID로 같은 광고를 60초 내 재클릭하면 잔액이 차감되지 않는다.

---

## 광고 상태 전환 규칙

```
ACTIVE ──[잔액 소진]──► EXHAUSTED ──[잔액 재충전]──► ACTIVE
ACTIVE ──[수동 중단]──► PAUSED    ──[수동 활성화]──► ACTIVE
PAUSED ──[잔액 재충전]──► PAUSED  (잔액만 증가, 상태 변경 없음)
```

---

## 테스트 전략 (Test Pyramid)

레이어별 무조건적인 테스트를 강요하지 않는다. 아래 피라미드 구조로 충분하다.

**Unit Test** — `domain/`, `application/` 레이어
- Spring 컨텍스트 없음, Mockito만 사용
- 예: `AdServiceTest`, `ClickFacadeServiceTest`

**Integration Test** — `infrastructure/` 레이어
- `@DataJpaTest`, `@DataRedisTest` + Testcontainers
- 예: `AdJpaRepositoryTest`, `ClickEventJpaRepositoryTest`, `ValkeyCacheServiceTest`

**E2E Test** — `interfaces/` 레이어 (Controller)
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers (MySQL + Valkey)
- 실제 HTTP 요청으로 전체 스택 검증
- 예: `AdApiE2ETest`, `ClickApiE2ETest`

---

## Valkey 키 구조

Valkey는 Redis 7.2 호환 오픈소스 포크입니다. Spring Data Redis를 그대로 사용합니다.

```
ad:rotation:queue              List   활성 광고 ID Round Robin 큐
ad:rotation:rebuild:lock       String SETNX 분산 락 (큐 재구성 시 단일 서버 보장)
abuse:{ip}:{adId}              String TTL 60s (IP 기반 중복 클릭 방지)
abuse:anon:{anonId}:{adId}     String TTL 60s (익명ID 기반 중복 클릭 방지)
```
