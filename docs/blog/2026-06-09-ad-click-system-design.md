# 광고 배너 클릭 한 번에 10원 — 정확히, 딱 한 번만: 클릭 집계 서비스 설계 전체 공개

> 다이어그램 파일: `docs/diagrams/system-architecture.excalidraw`, `docs/diagrams/click-flow.excalidraw`

---

## 들어가며

블로그 배너 광고를 클릭하는 건 1초도 안 걸리는 일입니다. 그런데 그 1초 안에 서버 입장에서는 꽤 많은 일이 일어나야 합니다.

> 이 클릭이 진짜 사람의 클릭인가? 같은 사람이 방금 전에도 클릭하지 않았나? 광고주 잔액이 아직 남아있나? 지금 여러 요청이 동시에 들어오고 있는 건 아닌가?

이 모든 질문에 답하면서 10원을 정확히 한 번만 차감해야 합니다. 이 글은 그 서비스를 설계하면서 고민했던 내용을 정리한 기록입니다.

---

## 서비스 개요

**광고 클릭 이벤트 집계 서비스**

- 광고주는 비용을 선결제
- 클릭이 발생할 때마다 10원 차감
- 잔액이 소진되면 해당 광고 자동 중단
- 등록된 광고는 특정 광고 편중 없이 균등하게 노출

---

## 요구사항 정의

기능 요구사항을 정리하면 6가지입니다.

| # | 요구사항 | 핵심 키워드 |
|---|----------|-------------|
| R1 | 클릭 발생 시 건당 10원 차감 | 선결제 후 차감 |
| R2 | 광고마다 충전 금액이 다를 수 있음 | 광고별 독립 잔액 |
| R3 | 잔액 소진 시 해당 광고 자동 중단 | 실시간 반영 |
| R4 | 균등 노출 (우선순위 없이) | 공정한 노출 |
| R5 | 어뷰징 방어 (중복 클릭, 비정상 트래픽) | 부당 과금 방지 |
| R6 | 동시성(따닥) 이슈 방어 | 잔액 초과 차감 방지 |

그리고 **규모 목표**입니다. MVP부터 시작해 단계별로 확장하는 것을 전제로 설계했습니다.

| 단계 | 목표 TPS |
|------|----------|
| 1단계 | 1 ~ 1,000 |
| 2단계 | 1,000 ~ 5,000 |
| 3단계 | 10,000+ |

---

## 기술 스택 선택과 그 이유

### Java / Spring Boot

엔터프라이즈 환경에서 검증된 안정성, Spring Data JPA·Redis 같은 풍부한 생태계가 있고, 이 서비스에 필요한 Bucket4j(Rate Limiting), `@Transactional`(동시성 제어) 등이 잘 통합됩니다.

### MySQL

잔액 차감과 충전 이력처럼 **정합성이 핵심인 데이터**는 RDBMS가 필수입니다. 트랜잭션과 `SELECT FOR UPDATE`(Pessimistic Lock)를 활용해 동시성을 제어하고, 나중에 Read Replica를 붙여 읽기 부하를 분산할 수 있습니다.

### Valkey (Redis 대신)

> 🔍 **확인 필요**: Redis의 SSPL 라이선스 전환은 2024년에 있었으며, 이후 Valkey 최신 버전과 AWS ElastiCache Valkey 지원 상태를 게시 전 확인하세요.

2024년 Redis가 오픈소스 라이선스(BSD)에서 **SSPL**로 전환했습니다. SSPL은 SaaS 형태로 서비스를 제공하는 경우 소스 공개 의무가 생기는 라이선스라 클라우드 사업자들이 Redis를 그대로 쓰기 어려워졌습니다. 이에 Redis 커뮤니티가 포크해서 만든 것이 **Valkey**입니다.

왜 Valkey를 선택했냐면:
- **Redis 7.2 API 완전 호환** — Spring Data Redis, Lua Script 그대로 사용 가능
- **AWS ElastiCache가 Valkey 공식 지원** — 배포 환경과 자연스럽게 연결
- **LocalStack으로 로컬 에뮬레이션 가능** — 개발 환경 이슈 없음

---

## 전체 아키텍처

> 다이어그램: `docs/diagrams/system-architecture.excalidraw` 참조

```
[Client]
   │
   ▼
[Spring Boot API Server]
   │
   ├─► [Valkey]
   │     ├─ Round Robin Queue       (활성 광고 ID 순환)
   │     ├─ 어뷰징 방어 TTL 키      (IP / 익명ID + 광고ID)
   │     └─ (2단계~) 잔액 캐시 + Lua Script 원자적 차감
   │
   └─► [MySQL]
         ├─ ads                     (광고 기본 정보)
         ├─ ad_balances             (광고별 잔액, Lock 대상)
         ├─ click_events            (클릭 원본 이벤트)
         └─ balance_transactions    (충전/차감 이력)
```

2단계에서 Kafka가 추가되면 `click_events` INSERT와 광고 상태 업데이트가 Consumer로 이동하고, DB 병목이 줄어듭니다. 이를 1단계부터 대비해 **Outbox 패턴** 구조를 코드 레벨에서 준비해뒀습니다 (뒤에서 설명).

---

## 핵심 설계 결정 6가지

### 1. 광고 균등 노출 — Round Robin

"우선순위 없이 균등하게"가 요구사항(R4)이었는데, 검토한 방식은 세 가지였습니다.

| 방식 | 탈락 이유 |
|------|-----------|
| **Round Robin** | **채택** |
| Weighted Round Robin | 잔액 많은 광고가 더 자주 노출 → 균등성 훼손 |
| 순수 랜덤 | 통계적 균등이지 실시간 균등 아님 (N번 호출 시 특정 광고가 N번 연속 선택될 수 있음) |

Valkey의 `LPOP/RPUSH`로 구현합니다. 큐에서 하나를 꺼내고(`LPOP`) 다시 맨 뒤에 넣으면(`RPUSH`) 자연스럽게 순환이 됩니다. 잔액이 소진된 광고는 큐에서 제거하면 되고요.

**다중 서버 환경의 함정**: Nginx 같은 LB 뒤에 서버가 여러 대 있으면, Caffeine 같은 로컬 캐시로는 순환 위치를 공유할 수 없습니다. 서버 1은 Ad A 차례인데, 서버 2도 Ad A 차례라면 균등 노출이 깨집니다. 그래서 Valkey를 공유 상태 저장소로 씁니다.

**Valkey 재시작 시 큐 재구성 문제**: 재시작하면 메모리가 초기화됩니다. 이때 큐가 비어있음을 감지하면 DB에서 ACTIVE 광고를 조회해 큐를 다시 채웁니다. 다중 서버에서 동시에 재구성하면 중복 진입이 발생할 수 있어 **SETNX 분산 락**으로 단 한 대만 재구성하도록 합니다.

```
LLEN ad:rotation:queue == 0 감지
   │
   ├─ SETNX ad:rotation:rebuild:lock "1" EX 5
   │     성공 → DB 조회 → RPUSH → 락 해제
   │     실패 → 다른 서버가 재구성 중 → 대기 후 재확인
```

---

### 2. 어뷰징 방어 — Bucket4j + Valkey 이중 방어

두 도구는 **방어 대상이 다릅니다**.

| 레이어 | 도구 | 방어 대상 |
|--------|------|-----------|
| API 레벨 | **Bucket4j** | IP당 초당/분당 전체 요청 수 제한 |
| 도메인 레벨 | **Valkey TTL 키** | 동일 사용자 + 동일 광고 60초 내 재클릭 무효화 |

Bucket4j만 쓰면 다른 광고를 빠르게 클릭하는 어뷰저를 잡기 어렵고, Valkey TTL만 쓰면 단순 DDoS성 트래픽이 DB까지 도달합니다. 두 레이어를 조합해 각각의 빈틈을 메웁니다.

**로그인 없이 사용자를 식별하는 방법**: 블로그 배너 광고의 방문자는 대부분 비로그인 상태입니다. 인증 시스템을 만드는 건 이 도메인을 벗어난 오버엔지니어링입니다. 대신 **브라우저 쿠키에 UUID를 발급**합니다.

- 첫 클릭 시 서버가 UUID 발급 → `Set-Cookie: anonymous_id=<uuid>; HttpOnly; SameSite=Lax`
- 이후 요청은 브라우저가 자동으로 쿠키 전송 → 클라이언트 코드 불필요
- 서버는 `HttpServletRequest`에서 쿠키를 읽어 `anonymous_id` 추출

Valkey TTL 키 구조:
```
abuse:{ip}:{adId}            TTL 60s  (IP 기반 재클릭 방지)
abuse:anon:{anonId}:{adId}   TTL 60s  (익명ID 기반 재클릭 방지)
```

**어뷰징 클릭은 삭제하지 않습니다.** `is_valid=false`로 기록을 남깁니다. 나중에 패턴 분석, 광고주 분쟁 대응 시 근거 데이터가 됩니다.

---

### 3. 잔액 차감 동시성 — 단계별 전환 전략

"따닥 이슈"는 광고주 돈이 오가는 영역이라 가장 신중하게 다뤄야 합니다. 검토한 방식들:

| 방식 | 장점 | 단점 |
|------|------|------|
| Pessimistic Lock | 구현 단순, 정합성 확실 | 고TPS에서 Lock 경합 병목 |
| Optimistic Lock | Lock 없이 처리 | 충돌 빈번 시 재시도 폭발 |
| Valkey Lua Script | 원자적 처리, 고성능 | Valkey 장애 시 리스크 |
| Kafka 직렬화 | 동시성 문제 구조적 제거 | 실시간 차감 불가, 지연 발생 |

**MVP에서 Lua Script부터 쓰는 건 불필요한 복잡도**입니다. 1단계에서는 구현 단순성을 택하고, 성능 한계에 도달하면 전환합니다.

| 단계 | 방식 | 전환 트리거 |
|------|------|-------------|
| 1단계 | Pessimistic Lock (`SELECT FOR UPDATE`) | 기본 |
| 2단계 | Valkey Lua Script | Lock 경합으로 응답 지연 시 |
| 3단계 | Valkey + Kafka 직렬화 | Valkey 단일 장애점 리스크 보완 시 |

---

### 4. Outbox 패턴 — Kafka 전환을 위한 준비

1단계에서는 트랜잭션 안에서 바로 `click_events`에 INSERT합니다. 그런데 나중에 Kafka를 붙이면 이 코드를 대수술해야 합니다.

지금부터 서비스 레이어를 분리해두면 2단계에서 `ClickEventService` 내부만 교체하면 됩니다.

```java
ClickFacadeService
  ├── BalanceService.deduct()      // 동기 트랜잭션 (변경 없음)
  └── ClickEventService.record()  // 1단계: 직접 INSERT
                                  // 2단계: Kafka publish 로 교체
```

Outbox 패턴은 트랜잭션 안에서 `outbox` 테이블에 이벤트를 함께 INSERT하고, 별도 프로세스가 이를 읽어 Kafka로 발행합니다. 트랜잭션 커밋과 이벤트 발행의 원자성이 보장됩니다.

---

### 5. Valkey 장애 시 Fallback 전략

Valkey가 다운되면 어뷰징 체크와 Round Robin 두 가지가 모두 멈춥니다. 각각 다른 전략이 필요합니다.

**어뷰징 체크: Fail Open**

Fail Closed(요청 전체 차단)를 선택하면 Valkey 장애 동안 모든 광고주의 서비스가 중단됩니다. Valkey 장애는 드물고 짧은 이벤트이고, 이 구간에 어뷰징이 집중될 가능성은 낮습니다. 장애 복구 후 배치 잡으로 중복 클릭을 탐지해 **광고주 잔액을 환불**하는 방식으로 보완합니다.

| 단계 | Fallback |
|------|----------|
| 1단계 | Fail Open + 사후 보정 배치 |
| 2단계 | Circuit Breaker (Resilience4j) + DB Fallback |
| 3단계 | Circuit Breaker + Caffeine 로컬 인메모리 |

**Round Robin: DB에서 랜덤 선택**

앞서 설명했듯, 다중 서버 환경에서 로컬 캐시로는 순환 위치를 공유할 수 없습니다. Valkey 장애 구간에 한해 DB에서 ACTIVE 광고를 가져와 **랜덤 선택**합니다. 통계적으로 균등하며, 단기 장애 구간에 허용 가능한 수준입니다.

---

### 6. 광고주 잔액 충전 — 자동 재활성화 규칙

결제 연동(PG사)은 이 서비스의 핵심 목적을 벗어납니다. MVP에서는 충전 API로 직접 잔액을 추가합니다.

충전 시 광고 상태 전환 규칙이 중요합니다.

```
ACTIVE    → 잔액만 증가 (상태 변경 없음)
EXHAUSTED → ACTIVE 자동 전환 + Valkey 큐 재진입
PAUSED    → 잔액만 증가 (상태 변경 없음, 수동 활성화 필요)
```

**EXHAUSTED는 자동 전환, PAUSED는 수동 전환인 이유**: EXHAUSTED 상태에서 충전한다는 것은 "광고를 다시 노출하고 싶다"는 의도가 명확합니다. 반면 PAUSED는 광고주가 직접 중단한 것이므로, 충전했다고 해서 자동으로 재노출되면 의도치 않은 노출이 발생할 수 있습니다.

---

## 데이터 모델

`ad_balances`를 `ads`에서 분리한 이유가 있습니다. 클릭이 발생하면 잔액 테이블에만 Lock이 걸립니다. 만약 같은 테이블이라면 광고 목록 조회처럼 잦은 읽기 작업이 모두 Lock의 영향을 받게 됩니다.

```sql
-- 광고 기본 정보
CREATE TABLE ads (
    id             BIGINT       PRIMARY KEY AUTO_INCREMENT,
    advertiser_id  BIGINT       NOT NULL,
    name           VARCHAR(255) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | PAUSED | EXHAUSTED
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL
);

-- 광고별 잔액 (ads와 분리하여 Lock 경합 최소화)
CREATE TABLE ad_balances (
    ad_id       BIGINT         PRIMARY KEY,
    balance     DECIMAL(15, 2) NOT NULL DEFAULT 0,
    updated_at  DATETIME       NOT NULL,
    FOREIGN KEY (ad_id) REFERENCES ads(id)
);

-- 클릭 이벤트 원본 (어뷰징 클릭도 is_valid=false로 보존)
CREATE TABLE click_events (
    id             BIGINT      PRIMARY KEY AUTO_INCREMENT,
    ad_id          BIGINT      NOT NULL,
    ip_address     VARCHAR(45) NOT NULL,
    anonymous_id   VARCHAR(64),
    clicked_at     DATETIME    NOT NULL,
    is_valid       BOOLEAN     NOT NULL DEFAULT TRUE,
    invalid_reason VARCHAR(30) NULL     -- DUPLICATE_IP | DUPLICATE_ANON | RATE_LIMIT
);

-- 충전/차감 전체 이력 (정산 및 분쟁 대응)
CREATE TABLE balance_transactions (
    id         BIGINT         PRIMARY KEY AUTO_INCREMENT,
    ad_id      BIGINT         NOT NULL,
    amount     DECIMAL(15, 2) NOT NULL,
    type       VARCHAR(10)    NOT NULL, -- CHARGE | DEDUCT
    created_at DATETIME       NOT NULL
);
```

**인덱스 설계도 중요합니다.**

```sql
-- 어뷰징 체크 DB Fallback + 사후 보정 배치
-- WHERE ad_id = ? AND ip_address = ? AND clicked_at > ?
INDEX idx_click_abuse (ad_id, ip_address, clicked_at)

-- 통계 조회
-- WHERE ad_id = ? AND clicked_at BETWEEN ? AND ? AND is_valid = ?
INDEX idx_click_stats (ad_id, clicked_at, is_valid)
```

---

## 클릭 처리 상세 흐름

> 다이어그램: `docs/diagrams/click-flow.excalidraw` 참조

```
POST /api/v1/ads/{adId}/clicks
   │
   ├─ [0] 쿠키 처리
   │       anonymous_id 없으면 UUID 발급 + Set-Cookie
   │
   ├─ [1] Bucket4j — IP Rate Limit 초과 → 429
   │
   ├─ [2] 광고 상태 체크
   │       PAUSED / EXHAUSTED / 없음 → 404
   │
   ├─ [3] Valkey TTL 중복 체크
   │       abuse 키 존재 → is_valid=false 기록 후 종료
   │
   └─ [4~7 단일 트랜잭션]
       ├─ [4] SELECT FOR UPDATE (ad_balances)
       ├─ [5] 잔액 10원 차감 + balance_transactions INSERT
       ├─ [6] click_events INSERT (is_valid=true)
       └─ [7] 잔액 소진 시 EXHAUSTED 전환 + Valkey 큐 제거
```

**[2] 광고 상태 체크를 Bucket4j 바로 다음에 두는 이유**: 비활성 광고에 대한 클릭은 어뷰징 기록도 의미가 없습니다. 가장 앞단에서 차단해 Valkey 조회, DB Lock 등 불필요한 처리를 방지합니다. 응답 코드를 404로 처리해 광고 존재 여부를 외부에 노출하지 않습니다.

**트랜잭션 범위를 [4~7]로 묶는 이유**: "잔액만 차감되고 클릭이 기록 안 됨" 또는 그 반대 상황을 방지하기 위해 원자적으로 처리합니다.

---

## 멀티모듈 구조

```
adclick/
├── apps/
│   ├── ad-api/           ← Spring Boot 실행 진입점, 공통 설정
│   ├── ad-management/    ← 광고 등록/관리 도메인
│   │   ├── interfaces/   ← Controller (REST API)
│   │   ├── application/  ← Service (유스케이스 조율)
│   │   ├── domain/       ← Entity, Value Object
│   │   └── infrastructure/ ← JPA Repository, Valkey 어댑터
│   └── ad-click/         ← 클릭 집계 도메인
│       ├── interfaces/
│       ├── application/  ← ClickFacadeService, ClickEventService
│       ├── domain/       ← ClickEvent, InvalidReason
│       └── infrastructure/ ← JPA, AbuseGuardAdapter
└── settings.gradle
```

의존 방향은 단방향으로 엄수합니다.

```
ad-api → ad-management
ad-api → ad-click
ad-click → ad-management   (잔액 차감을 위해)

ad-management은 ad-click을 절대 알지 못합니다.
```

---

## 구현 우선순위

이 시스템의 본질을 한 줄로 요약하면:

> **"클릭 한 번에 10원이 정확히 한 번만 차감되고, 잔액이 0이 되는 순간 광고가 멈춰야 한다."**

| 순위 | 구간 | 오류 발생 시 영향 | 핵심 방어 수단 |
|------|------|------------------|----------------|
| 1 | 잔액 차감 정합성 | 즉각 금전 피해 | Pessimistic Lock, 단일 트랜잭션 |
| 2 | 잔액 소진 즉시 중단 | 초과 과금 | 트랜잭션 내 상태 전환 + Valkey 큐 제거 |
| 3 | 어뷰징 방어 | 부당 과금 | Bucket4j + Valkey TTL + 사후 보정 배치 |
| 4 | Round Robin 균등 노출 | 공정성 문제 | Valkey LPOP/RPUSH + SETNX 재구성 |

Round Robin이 4순위인 이유: 잘못돼도 직접적인 금전 오류가 아닙니다. Valkey 장애 시 랜덤 선택으로 대체해도 단기적으로 허용 가능한 이유이기도 합니다.

---

## 배운 점

**트레이드오프를 명시적으로 기록하는 것의 가치**: 설계를 하면서 매 결정마다 "왜 이걸 선택했는가, 왜 나머지는 버렸는가"를 적었습니다. 3개월 후에 같은 팀원이(혹은 미래의 내가) 코드를 보면서 "왜 Lua Script 안 쓰고 Pessimistic Lock을 썼지?"라고 의아해할 수 있습니다. 그 맥락이 문서에 있으면 불필요한 재논의가 사라집니다.

**MVP에서 확장을 고려하는 방법**: "나중에 Kafka 붙이면 다 바꿔야겠지"가 아니라, 지금 코드 레이어를 어떻게 분리하면 나중에 교체 범위가 최소화되는지를 생각했습니다. `ClickEventService.record()`를 분리해두는 것처럼, 교체 지점을 좁혀두는 것이 점진적 확장의 핵심입니다.

**Fail Open vs Fail Closed의 판단 기준**: "장애 시 어떤 피해가 더 큰가"입니다. Valkey 장애 시 어뷰징 체크를 건너뛰어 부당 과금이 발생할 수 있지만, 대신 배치 환불로 보정할 수 있습니다. 반면 Fail Closed를 선택하면 장애 동안 모든 광고주의 서비스가 중단됩니다. 복구 가능성과 영향 범위를 함께 따지는 것이 맞습니다.

**테이블 분리도 동시성 설계다**: `ad_balances`를 `ads`에서 분리한 건 단순한 정규화가 아닙니다. Lock 경합 범위를 좁히기 위한 의식적인 설계 결정입니다. 어떤 테이블에 Lock이 걸리는지를 생각하면 테이블 설계가 달라집니다.

---

## 참고자료

> 🔍 **게시 전 확인 필요**: 아래 링크의 최신 버전 및 변경사항을 2026년 5월 기준으로 확인하세요.

- [Valkey 공식 사이트](https://valkey.io)
- [Redis SSPL 라이선스 변경 공지 (2024)](https://redis.io/blog/redis-adopts-dual-source-available-licensing/)
- [AWS ElastiCache for Valkey](https://aws.amazon.com/elasticache/valkey/)
- [Bucket4j 공식 문서](https://bucket4j.com)
- [Spring Data Redis 공식 문서](https://docs.spring.io/spring-data/redis/reference/)
- [Outbox Pattern — microservices.io](https://microservices.io/patterns/data/transactional-outbox.html)
