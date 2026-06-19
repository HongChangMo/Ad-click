# 광고 클릭 이벤트 집계 서비스 — 시스템 설계 문서

> 작성일: 2026-06-09  
> 작성 목적: 설계 결정 근거 및 요구사항 충족 여부 공유

---

## 목차

1. [서비스 개요](#1-서비스-개요)
2. [요구사항 정의](#2-요구사항-정의)
3. [기술 스택 선택과 근거](#3-기술-스택-선택과-근거)
4. [전체 아키텍처](#4-전체-아키텍처)
5. [핵심 설계 결정과 근거](#5-핵심-설계-결정과-근거)
   - 5.1 광고 균등 노출
   - 5.2 어뷰징 방어
   - 5.3 잔액 차감 동시성
   - 5.4 클릭 이벤트 저장 (Outbox 패턴)
   - 5.5 Valkey 장애 시 Fallback 전략
   - 5.6 광고주 잔액 충전
6. [데이터 모델](#6-데이터-모델)
7. [API 설계](#7-api-설계)
8. [클릭 처리 상세 흐름](#8-클릭-처리-상세-흐름)
9. [단계별 확장 전략](#9-단계별-확장-전략)
10. [멀티모듈 구조 및 패키지 설계](#10-멀티모듈-구조-및-패키지-설계)
11. [시스템 핵심 구간 및 구현 우선순위](#11-시스템-핵심-구간-및-구현-우선순위)

---

## 1. 서비스 개요

블로그 배너 광고의 클릭 이벤트를 수집·집계하는 서비스입니다.  
광고주는 비용을 선결제하고, 클릭 발생 시 건당 10원이 차감됩니다.  
잔액이 소진되면 광고가 자동 중단되며, 시스템에 등록된 광고는 균등하게 노출됩니다.

---

## 2. 요구사항 정의

| # | 요구사항 | 비고 |
|---|----------|------|
| R1 | 조회(노출) 발생 시 10원, 클릭 발생 시 50원 차감 | 선결제 후 차감 방식, 광고주 잔액에서 이벤트별 차감 |
| R2 | 광고마다 충전 금액이 다를 수 있음 | 광고별 독립 잔액 관리 |
| R3 | 잔액 소진 시 해당 광고 자동 중단 | 실시간 반영 필요 |
| R4 | 노출 광고는 우선순위 없이 균등 분배 | 특정 광고 편중 방지 |
| R5 | 어뷰징 행위 방어 | 중복 클릭, 비정상 트래픽 차단 |
| R6 | 동시성(따닥) 이슈 방어 | 잔액 초과 차감 방지 |

**서비스 규모 목표**

| 단계 | 규모 | TPS |
|------|------|-----|
| 1단계 | 소규모 MVP | 1 ~ 1,000 |
| 2단계 | 중규모 MVP | 1,000 ~ 5,000 |
| 3단계 | 대규모 MVP | 10,000 이상 |

---

## 3. 기술 스택 선택과 근거

### 3.1 Java / Spring Boot

**선택 이유**
- 엔터프라이즈 환경에서 검증된 안정성
- Spring Data JPA, Spring Data Redis 등 풍부한 생태계
- Bucket4j, Transactional 어노테이션 등 이 서비스에 필요한 기능들이 잘 통합됨

### 3.2 MySQL

**선택 이유**
- 잔액 차감, 충전 이력 등 정합성이 중요한 데이터를 다루므로 RDBMS 필수
- 트랜잭션과 Pessimistic Lock 지원으로 동시성 제어 가능
- 추후 Read Replica 확장으로 읽기 부하 분산 가능

### 3.3 Valkey (Redis 대체)

**선택 이유**
- Redis가 2024년 라이선스를 SSPL로 변경함에 따라, 오픈소스 포크인 Valkey 선택
- Redis 7.2와 완전 API 호환 — Spring Data Redis, Lua Script 그대로 사용 가능
- AWS ElastiCache가 Valkey를 공식 지원 (배포 환경과 자연스럽게 연결)
- LocalStack을 통해 로컬 개발 환경에서도 동일하게 구동 가능

**Valkey의 역할**
- Round Robin Queue로 광고 균등 노출 관리
- 어뷰징 방어용 TTL 키 저장
- 2단계부터 잔액 캐시 및 Lua Script 기반 원자적 차감

**Valkey 재시작 시 Round Robin Queue 재구성 전략**  
Valkey 재시작 시 메모리의 Round Robin Queue가 초기화됩니다.  
`GET /api/v1/ads/next` 호출 시 큐가 비어있음을 감지하고 그 시점에 DB에서 재구성합니다.  
다중 서버 환경에서 동시 재구성으로 인한 중복 진입을 방지하기 위해 단계별로 방어 전략을 강화합니다.

| 단계 | 동시 재구성 방어 방식 | 이유 |
|------|----------------------|------|
| 1단계 | **SETNX 분산 락** | 구현 단순, 다중 서버 경합 방지 |
| 2단계 | **Lua Script 원자적 초기화** | 락 관리 불필요, 원자성 보장 |
| 3단계 | **synchronized + SETNX 조합** | 서버 내/서버 간 이중 방어 |

```
[1단계 SETNX 재구성 흐름]
LLEN ad:rotation:queue == 0 감지
   │
   ├─ SETNX ad:rotation:rebuild:lock "1" EX 5
   │     성공 → DB에서 ACTIVE 광고 조회 → RPUSH → 락 해제
   │     실패 → 다른 서버가 재구성 중 → 대기 후 LLEN 재확인
   │
   └─ 재구성 완료 → 정상 LPOP/RPUSH 순환 재개
```

---

## 4. 전체 아키텍처

> 단계별 아키텍처 다이어그램: `docs/diagrams/stage1-architecture.excalidraw` / `stage2-architecture.excalidraw` / `stage3-architecture.excalidraw`

**1단계 (현재 구현)**

```
[Client]
   │
   ▼
[Spring Boot API Server (EC2 단일)]
   │   잔액 차감: Pessimistic Lock
   │   click_events: 직접 MySQL INSERT
   │
   ├─► [Valkey (ElastiCache)]
   │     ├─ Round Robin Queue       (활성 광고 ID 순환)
   │     └─ 어뷰징 방어 TTL 키      (IP/익명ID + 광고ID)
   │
   └─► [MySQL (RDS)]
         ├─ ads                     (광고 기본 정보)
         ├─ ad_balances             (광고별 잔액, Pessimistic Lock 대상)
         ├─ click_events            (클릭 원본 이벤트)
         └─ balance_transactions    (충전/차감 전체 이력)
```

**2단계 이후 (Nginx LB + Kafka 추가)**

```
[Client]
   │
   ▼
[Nginx Load Balancer]
   │
   ├─► [API Server 1]  [API Server 2]   ← Circuit Breaker (Resilience4j)
   │        │
   │   잔액 차감: Valkey Lua Script (원자적)
   │   Fallback: DB Random 선택
   │
   ├─► [Valkey]          (+ 잔액 캐시 + Lua Script)
   ├─► [MySQL Primary]   (+ Read Replica, + Outbox 테이블)
   │         └─► [Read Replica]
   └─► [Kafka MSK]
         └─► [Consumer]  (click_events INSERT, 광고 상태 업데이트, 통계 집계)
```

**3단계 이후 (Auto Scaling + HA)**

```
[Nginx LB (Auto Scaling)]
   │
   └─► [Auto Scaling Group — API Server × N]
             │   Circuit Breaker + Caffeine 로컬 캐시 Fallback
             │
   ├─► [Valkey HA (Multi-AZ)]     ← Replica 자동 승격
   ├─► [MySQL Multi-AZ]           ← Primary + Standby, 자동 Failover
   │         └─► [Read Replicas × N]
   └─► [Kafka MSK (Multi-AZ)]
         └─► [Consumers (Auto Scaled)]
```

---

## 5. 핵심 설계 결정과 근거

### 5.1 광고 균등 노출 — Round Robin (R4 충족)

**검토한 대안**

| 방식 | 특징 | 탈락 이유 |
|------|------|-----------|
| Round Robin | 순서대로 순환 노출 | **채택** |
| Weighted Round Robin | 잔액 비율을 가중치로 반영 | 잔액 많은 광고가 더 노출 → 균등성 훼손 |
| 순수 랜덤 | 무작위 선택 | 통계적 균등이지 실시간 균등 아님 |

**채택 근거**  
요구사항이 "우선순위 없이 최대한 균등하게"이므로 Round Robin이 의도에 가장 부합합니다.  
Valkey의 `LPOP/RPUSH`로 원자적 처리가 가능하고, 잔액 소진 시 해당 광고를 큐에서 제거하면 자연스럽게 활성 광고 간 균등 노출이 유지됩니다.

---

### 5.2 어뷰징 방어 — Bucket4j + Valkey 이중 방어 (R5 충족)

**왜 두 가지를 함께 쓰는가?**

두 도구는 방어하는 대상이 다릅니다.

| 레이어 | 도구 | 방어 대상 |
|--------|------|-----------|
| API 레벨 | **Bucket4j + Valkey** | IP당 초당/분당 전체 요청 수 제한 |
| 도메인 레벨 | **Valkey TTL 키** | 동일 사용자 + 동일 광고 중복 클릭 무효화 |

**익명 ID 기반 사용자 식별 (로그인 불필요)**  
블로그 배너 광고의 특성상 방문자 대부분이 비로그인 상태입니다.  
별도 인증 시스템을 구축하는 것은 이 서비스 범위를 벗어나는 오버엔지니어링이므로,  
브라우저 쿠키에 UUID를 발급해 익명 ID로 사용자를 식별합니다.

**anonymous_id 쿠키 발급 및 전달 방식**  
- 클릭 요청 수신 시 `anonymous_id` 쿠키가 없으면 서버에서 UUID를 발급하고 `Set-Cookie`로 응답
- 이후 요청부터 브라우저가 자동으로 쿠키를 전송하므로 클라이언트 구현 불필요
- 쿠키 속성: `HttpOnly`, `SameSite=Lax`, 만료 없음(브라우저 세션 유지)
- 서버는 `HttpServletRequest`에서 쿠키를 직접 읽어 `anonymous_id` 추출

**Valkey TTL 키 구조**
```
abuse:{ip}:{adId}            TTL 60s  (IP 기반 동일 광고 재클릭 방지)
abuse:anon:{anonId}:{adId}   TTL 60s  (익명ID 기반 동일 광고 재클릭 방지)
```

**어뷰징 클릭 처리 방침**  
어뷰징으로 판단된 클릭은 삭제하지 않고 `is_valid=false`로 기록합니다.  
이는 이후 패턴 분석, 이상 감지, 광고주 분쟁 대응 시 근거 데이터로 활용하기 위함입니다.

---

### 5.3 잔액 차감 동시성 — 단계별 전환 전략 (R6 충족)

**검토한 대안**

| 방식 | 장점 | 단점 |
|------|------|------|
| Pessimistic Lock (SELECT FOR UPDATE) | 구현 단순, 정합성 확실 | 고TPS에서 Lock 경합 병목 |
| Optimistic Lock | Lock 없이 처리 | 충돌 빈번 시 재시도 폭발 |
| Redis Lua Script | 원자적 처리, 고성능 | Redis 장애 시 리스크 |
| Redlock | 분산 락, 직렬 처리 보장 | 처리량 낮음, 구현 복잡 |
| Kafka 직렬화 | 동시성 문제 구조적 제거 | 실시간 차감 불가, 지연 발생 |

**채택 전략 — 단계별 전환**

소규모 MVP에서 Lua Script부터 도입하는 것은 불필요한 복잡도를 초기에 가져오는 것입니다.  
1단계에서는 구현 단순성을, 이후 성능 한계에 도달하면 전환하는 점진적 접근을 선택했습니다.

| 단계 | 방식 | 전환 시점 |
|------|------|-----------|
| 1단계 | Pessimistic Lock | 기본 |
| 2단계 | Valkey Lua Script | Lock 경합으로 응답 지연 발생 시 |
| 3단계 | Valkey + Kafka 직렬화 | Valkey 단일 장애점 리스크 보완 시 |

---

### 5.4 클릭 이벤트 저장 — Outbox 패턴으로 Kafka 전환 준비 (확장성)

**현재 구조의 문제점**  
클릭 이벤트 저장(`click_events INSERT`)과 잔액 차감을 단일 트랜잭션으로 묶으면,  
추후 Kafka를 도입할 때 "잔액 차감은 동기, 이벤트 기록은 비동기"로 분리하기 어렵습니다.

**Outbox 패턴 적용**  
트랜잭션 내에서 `outbox` 테이블에 이벤트를 함께 INSERT하고,  
별도 프로세스가 outbox를 읽어 Kafka로 발행하는 방식입니다.  
이렇게 하면 트랜잭션 커밋과 이벤트 발행의 원자성이 보장됩니다.

```
[1단계] 트랜잭션
  잔액 차감 → click_events 직접 INSERT → 광고 상태 업데이트

[2단계] 트랜잭션
  잔액 차감 → outbox INSERT
  (별도) OutboxPublisher → Kafka 발행
  (별도) Kafka Consumer → click_events INSERT, 광고 상태 업데이트, 통계 집계
```

**코드 레벨 분리 (1단계부터 적용)**  
지금부터 서비스 레이어를 분리해두면 2단계 전환 시 `ClickEventService` 내부만 교체하면 됩니다.

```java
ClickFacadeService
  ├── BalanceService.deduct()      // 동기 트랜잭션 (변경 없음)
  └── ClickEventService.record()  // 1단계: 직접 INSERT
                                  // 2단계: Kafka publish 로 교체
```

---

### 5.5 Valkey 장애 시 Fallback 전략

Valkey는 어뷰징 체크와 Round Robin Queue 두 가지 핵심 기능에 사용됩니다.  
장애 시 각각 다른 전략을 적용합니다.

#### 어뷰징 체크 Fallback — Fail Open + 사후 보정 배치

| 옵션 | 설명 | 탈락/채택 이유 |
|------|------|----------------|
| Fail Closed | 클릭 요청 전체 차단 | 탈락 — 장애 동안 모든 광고주 서비스 중단, 과도한 영향 |
| Fail Open | 어뷰징 체크 건너뛰고 허용 | **채택** — 장애는 단기 이벤트, 사후 보정으로 금전 피해 보전 |

**채택 근거**  
Valkey 장애는 드물고 짧은 이벤트입니다. 이 구간에 어뷰징이 집중될 가능성은 낮으며,  
Fail Closed로 전체 서비스를 중단하는 것이 오히려 더 큰 피해입니다.  
장애 복구 후 배치 잡으로 중복 클릭을 탐지해 광고주 잔액을 환불하는 방식으로 보완합니다.

**추후 보완 경로 (단계별)**

| 단계 | Fallback 방식 |
|------|--------------|
| 1단계 | Fail Open + 사후 보정 배치 |
| 2단계 | Circuit Breaker (Resilience4j) + DB Fallback 어뷰징 체크 |
| 3단계 | Circuit Breaker + Caffeine 로컬 인메모리 Fallback |

#### Round Robin Queue Fallback — 다중 서버 환경 고려

**다중 서버(Nginx LB) 환경에서의 핵심 문제**  
Round Robin은 공유 상태(현재 순환 위치)가 필요합니다.  
Caffeine 로컬 캐시는 광고 **목록**은 서버 간 동일하게 유지할 수 있지만,  
**순환 위치**는 서버마다 독립적으로 관리되어 균등 보장이 깨집니다.

```
[Nginx LB]
   ├─► Server 1 (로컬 캐시: 현재 Ad B)
   ├─► Server 2 (로컬 캐시: 현재 Ad A)  → Ad A 중복 노출 발생
   └─► Server 3 (로컬 캐시: 현재 Ad A)
```

정상 동작 시에는 Valkey가 모든 서버에서 공유되므로 Round Robin이 정확하게 동작합니다.  
문제는 Valkey 장애 구간뿐이므로, 이 기간에는 **랜덤 선택**으로 대체합니다.  
랜덤은 순차적 균등은 아니지만 통계적으로 균등하며, 단기 장애 구간에 허용 가능한 수준입니다.

| 단계 | 정상 동작 | Valkey 장애 Fallback |
|------|-----------|----------------------|
| 1단계 | Valkey Round Robin | DB 조회 → ACTIVE 광고 중 랜덤 선택 |
| 2단계 | Valkey Round Robin | Caffeine 로컬 캐시(목록만) + 랜덤 선택 |
| 3단계 | Valkey HA (Multi-AZ) | Replica 자동 승격 + 전환 중 2단계 Fallback |

#### Circuit Breaker 동작 방식 및 Fallback 기본값 (2단계 이후)

Circuit Breaker가 없으면 Valkey 장애 시 매 요청마다 연결 타임아웃까지 기다린 후 Fallback으로 전환됩니다.  
Valkey 타임아웃이 2초라면 초당 수백 요청이 모두 2초씩 묶여 스레드 풀이 고갈됩니다.

Circuit Breaker(Resilience4j)는 연속 N번 실패를 감지하면 OPEN 상태로 전환하고,  
이후 모든 Valkey 호출을 건너뛰고 즉시 Fallback 메서드를 실행합니다.

```
[정상]  요청 → Valkey 호출 → 응답
[장애]  요청 1~N → 실패 감지 → Circuit OPEN
        요청 N+1 이후 → Valkey 호출 생략 → 즉시 Fallback 실행
[복구]  일정 시간 후 HALF-OPEN → Valkey 회복 확인 → Circuit CLOSED
```

**컴포넌트별 Fallback 기본값**

| 컴포넌트 | 메서드 | Fallback 반환값 | 손실 |
|---------|--------|----------------|------|
| `AbuseGuardAdapter.isAbuser()` | Valkey TTL 키 조회 | `false` (어뷰저 아님) | 중복 클릭 방어 일시 중단 |
| `ValKeyRotationAdapter.getNextAdId()` | Valkey LPOP | DB에서 ACTIVE 광고 랜덤 조회 | Round Robin → 랜덤 노출 |
| `BalanceAdapter.deductBalance()` (2단계) | Valkey Lua Script | DB Pessimistic Lock으로 전환 | 처리 속도 저하 |

```java
// 어뷰징 체크 — Fail Open
@CircuitBreaker(name = "valkey", fallbackMethod = "isAbuserFallback")
public boolean isAbuser(String ip, Long adId) {
    // Valkey TTL 키 조회
}

private boolean isAbuserFallback(String ip, Long adId, Exception e) {
    return false; // "어뷰저 아님"으로 처리 → 클릭 허용
}

// Round Robin — DB 랜덤 조회
@CircuitBreaker(name = "valkey", fallbackMethod = "getNextAdFallback")
public Long getNextAdId() {
    // Valkey LPOP
}

private Long getNextAdFallback(Exception e) {
    return adRepository.findRandomActiveAdId()
            .orElseThrow(NoActiveAdException::new);
}

// 잔액 차감 (2단계) — DB Pessimistic Lock
@CircuitBreaker(name = "valkey", fallbackMethod = "deductBalanceFallback")
public boolean deductBalance(Long adId) {
    // Valkey Lua Script
}

private boolean deductBalanceFallback(Long adId, Exception e) {
    return deductWithDbLock(adId); // SELECT ... FOR UPDATE
}
```

세 경우 모두 **"서비스 가용성"을 "정확성"보다 우선**합니다.  
Valkey가 죽어도 광고 클릭은 계속 처리되며, 어뷰징 방어와 균등 노출은 장애 구간에만 일시적으로 품질이 낮아집니다.

---

### 5.6 광고주 잔액 충전 — 단순 충전 API + 자동 재활성화

결제 연동(PG사)은 이 서비스의 핵심 목적인 클릭 집계 도메인에서 벗어납니다.  
MVP에서는 충전 API로 직접 잔액을 추가하는 방식으로 구현하되,  
`BalanceService` 레이어를 분리해두어 추후 PG 연동 시 해당 레이어만 교체 가능하도록 합니다.

**잔액 재충전 시 광고 상태 전환 흐름**

충전 목적 자체가 광고 재노출이므로, `EXHAUSTED` 상태에서 충전 시 자동으로 `ACTIVE`로 전환합니다.  
단, `PAUSED`(광고주가 수동 중단)는 충전해도 자동 활성화하지 않습니다.

```
POST /api/v1/ads/{adId}/balance/charge
   │
   ├─ [트랜잭션]
   │   ├─ ad_balances 잔액 증가
   │   ├─ balance_transactions INSERT (type=CHARGE)
   │   └─ 광고 상태가 EXHAUSTED 인 경우
   │         → ads 상태 ACTIVE 변경
   │
   └─ [트랜잭션 커밋 후]
         광고 상태가 EXHAUSTED → ACTIVE 로 전환된 경우
         → Valkey Round Robin Queue 에 adId 재진입 (RPUSH)

광고 상태별 충전 후 동작:
  EXHAUSTED → ACTIVE 자동 전환 + Valkey 큐 재진입
  PAUSED    → 잔액만 증가, 상태 변경 없음 (수동 활성화 필요)
  ACTIVE    → 잔액만 증가
```

---

## 6. 데이터 모델

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

-- 클릭 이벤트 원본
CREATE TABLE click_events (
    id            BIGINT      PRIMARY KEY AUTO_INCREMENT,
    ad_id         BIGINT      NOT NULL,
    ip_address    VARCHAR(45) NOT NULL,
    anonymous_id  VARCHAR(64),
    clicked_at    DATETIME    NOT NULL,
    is_valid      BOOLEAN     NOT NULL DEFAULT TRUE, -- 어뷰징 클릭도 기록 보존
    invalid_reason VARCHAR(30) NULL    -- DUPLICATE_IP | DUPLICATE_ANON | RATE_LIMIT (is_valid=false 시 사유)
);

-- 어뷰징 체크 (DB Fallback) + 사후 보정 배치용
--   WHERE ad_id = ? AND ip_address = ? AND clicked_at > ?
INDEX idx_click_abuse (ad_id, ip_address, clicked_at),

-- 통계 조회 + 유효 클릭 필터링용
--   WHERE ad_id = ? AND clicked_at BETWEEN ? AND ? AND is_valid = ?
INDEX idx_click_stats (ad_id, clicked_at, is_valid);

-- 충전/차감 전체 이력 (정산 및 분쟁 대응)
CREATE TABLE balance_transactions (
    id          BIGINT         PRIMARY KEY AUTO_INCREMENT,
    ad_id       BIGINT         NOT NULL,
    amount      DECIMAL(15, 2) NOT NULL,
    type        VARCHAR(10)    NOT NULL, -- CHARGE | VIEW | CLICK | REFUND
    created_at  DATETIME       NOT NULL
);

-- Outbox (2단계 Kafka 도입 시 활성화)
CREATE TABLE outbox_events (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    aggregate_id BIGINT       NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSON         NOT NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME     NOT NULL
);
```

**`ad_balances`를 `ads`에서 분리한 이유**  
클릭 발생 시 잔액 테이블에만 Lock이 걸리므로, 광고 정보 조회(`ads`)와 Lock 경합이 발생하지 않습니다.  
광고 목록 조회, 노출 처리 등 읽기 작업이 잔액 차감 Lock의 영향을 받지 않습니다.

---

## 7. API 설계

```
# 광고 관리
POST   /api/v1/ads                        광고 등록
PATCH  /api/v1/ads/{adId}/status          광고 상태 변경 (수동 PAUSE/ACTIVE)

# 잔액 관리
POST   /api/v1/ads/{adId}/balance/charge  잔액 충전
GET    /api/v1/ads/{adId}/balance         잔액 조회

# 광고 노출 및 클릭
GET    /api/v1/ads/next                   다음 노출 광고 조회 (Round Robin, 조회 시 10원 차감)
POST   /api/v1/ads/{adId}/clicks          클릭 이벤트 수신 (클릭 시 50원 차감)

# 통계
GET    /api/v1/ads/{adId}/clicks/stats    클릭 통계 조회
```

---

## 8. 이벤트 처리 상세 흐름

### 8.1 조회 처리 흐름 (GET /api/v1/ads/next)

```
GET /api/v1/ads/next
   │
   ├─ [1] Round Robin Queue (Valkey LPOP)
   │       큐 비어있음 → SETNX 락으로 단일 재구성 (DB ACTIVE 광고 RPUSH)
   │       Valkey 장애 → DB에서 ACTIVE 광고 랜덤 선택 (Fallback)
   │
   ├─ [2~3 단일 트랜잭션]
   │   ├─ [2] 잔액 10원 차감 + balance_transactions INSERT (type=VIEW)
   │   └─ [3] 잔액 소진 시 ads 상태 EXHAUSTED 업데이트
   │
   ├─ [4] Valkey RPUSH (Round Robin 순환 유지)
   │
   └─ 200 OK 응답 (AdInfo)
```

### 8.2 클릭 처리 흐름 (POST /api/v1/ads/{adId}/clicks)

```
POST /api/v1/ads/{adId}/clicks
   │
   ├─ [0] 쿠키 처리
   │       anonymous_id 쿠키 존재 → 추출하여 사용
   │       쿠키 없음 → UUID 신규 발급 + 응답에 Set-Cookie 추가
   │
   ├─ [1] Bucket4j
   │       IP 기반 Rate Limit 초과 → 429 Too Many Requests
   │
   ├─ [2] AdService (광고 상태 체크)
   │       ads 조회 → PAUSED / EXHAUSTED → 404 Not Found (광고 비활성 노출 방지)
   │       존재하지 않는 광고 → 404 Not Found
   │
   ├─ [3] AbuseGuardService (Valkey TTL)
   │       abuse:{ip}:{adId} 존재 → is_valid=false 로 click_events 기록 후 종료
   │       abuse:anon:{anonId}:{adId} 존재 → 동일 처리
   │
   ├─ [4~7 단일 트랜잭션]
   │   ├─ [4] BalanceService
   │   │       ad_balances SELECT FOR UPDATE
   │   │       잔액 < 50원 → 광고 EXHAUSTED 처리 + Valkey 큐 제거
   │   ├─ [5] 잔액 50원 차감 + balance_transactions INSERT (type=CLICK)
   │   ├─ [6] click_events INSERT (is_valid=true)
   │   └─ [7] 잔액 소진 시 ads 상태 EXHAUSTED 업데이트
   │
   └─ 200 OK 응답
```

**[2] 광고 상태 체크를 Bucket4j 직후에 두는 이유**  
비활성 광고(PAUSED/EXHAUSTED)에 대한 클릭은 어뷰징 기록도 의미가 없습니다.  
가장 앞단에서 차단해 이후 Valkey 조회, DB Lock 등 불필요한 처리를 방지합니다.  
응답 코드를 404로 처리해 광고 존재 여부 자체를 노출하지 않습니다.

**트랜잭션 범위를 [4~7]로 묶는 이유**  
잔액 확인, 차감, 이벤트 기록이 원자적으로 처리되어야 합니다.  
트랜잭션 중 장애 발생 시 잔액만 차감되고 클릭이 기록되지 않거나, 그 반대 상황을 방지합니다.

---

## 9. 단계별 확장 전략

> 아키텍처 다이어그램
> - 1단계: `docs/diagrams/stage1-architecture.excalidraw`
> - 2단계: `docs/diagrams/stage2-architecture.excalidraw`
> - 3단계: `docs/diagrams/stage3-architecture.excalidraw`

| 항목 | 1단계 (~1,000 TPS) | 2단계 (~5,000 TPS) | 3단계 (10,000+ TPS) |
|------|--------------------|--------------------|----------------------|
| API 서버 | EC2 단일 | EC2 × 2 + Nginx LB | Auto Scaling Group + Nginx LB |
| 잔액 차감 동시성 | Pessimistic Lock | Valkey Lua Script | Valkey Lua Script |
| Valkey 구성 | 단일 노드 | 단일 노드 (+ Lua Script, 잔액 캐시) | HA Multi-AZ (Replica 자동 승격) |
| Valkey Fallback | DB 조회 → ACTIVE 랜덤 | Circuit Breaker + DB Random | Circuit Breaker + Caffeine 로컬 캐시 |
| 클릭 이벤트 기록 | 직접 MySQL INSERT | Outbox → Kafka Consumer | Outbox → Kafka Consumer |
| 광고 상태 업데이트 | 동기 처리 | Kafka Consumer | Kafka Consumer |
| DB 구성 | MySQL 단일 | MySQL Primary + Read Replica | MySQL Multi-AZ + Read Replicas × N |
| Consumer | 없음 | Kafka Consumer (고정) | Consumers (Auto Scaled) |
| 인프라 | EC2 + RDS + ElastiCache(Valkey) | + Nginx + Read Replica + Kafka(MSK) | + Auto Scaling + Multi-AZ + Kafka HA |
| 로컬 개발 환경 | Testcontainers (MySQL + Valkey) | + Testcontainers Kafka | 동일 |

**각 전환 시점의 트리거**

- **1단계 → 2단계**: 잔액 차감 응답 시간이 증가하거나 Lock 타임아웃 에러 발생 시
- **2단계 → 3단계**: DB Write 병목이 CPU/IO 한계에 도달하거나 Valkey 단일 장애점 리스크가 커질 때

---

## 10. 멀티모듈 구조 및 패키지 설계

### 모듈 구성 원칙

- **Pattern B**: 기능(도메인) 단위로 모듈 분리, 각 모듈이 Layered Architecture 보유
- **각 도메인 모듈이 interfaces 레이어 포함**: 도메인 경계가 Controller까지 완전히 분리
- **`ad-api`**: `@SpringBootApplication`과 공통 설정만 담당하는 순수 실행 진입점

### 전체 멀티모듈 구조

> **DDD 4계층 패키지 구조 적용** (interfaces → application → domain ← infrastructure)
> - `Facade`: 유스케이스 조율, 트랜잭션 경계
> - `Info`: Facade 반환 객체 (domain entity 직접 노출 금지)
> - `RepositoryAdapter`: domain Repository 인터페이스를 JPA로 구현 (Adapter 패턴)

```
adclick/ (root)
├── apps/
│   │
│   ├── ad-api/                        ← Spring Boot 실행 모듈 (@SpringBootApplication)
│   │   └── src/main/java/
│   │       ├── AdClickApplication.java
│   │       └── config/
│   │             ValKeyConfig.java, JpaConfig.java
│   │
│   ├── ad-management/                 ← 광고 등록/관리 도메인
│   │   └── src/main/java/
│   │       ├── interfaces/api/
│   │       │     AdController.java          (POST /ads, PATCH /ads/{id}/status, GET /ads/{id})
│   │       │     BalanceController.java      (POST /ads/{id}/balance/charge, GET /ads/{id}/balance)
│   │       │     AdRotationController.java   (GET /ads/next)          ← 미구현
│   │       │   dto/
│   │       │     AdRegisterRequest.java, AdStatusChangeRequest.java
│   │       │     BalanceChargeRequest.java
│   │       ├── application/
│   │       │     AdFacade.java               ← 광고 등록/상태 변경
│   │       │     BalanceFacade.java          ← 잔액 충전, EXHAUSTED→ACTIVE 전환
│   │       │     AdRotationFacade.java       ← Valkey Round Robin Queue 관리  ← 미구현
│   │       │     AdNotFoundException.java
│   │       │   info/
│   │       │     AdInfo.java, BalanceInfo.java
│   │       ├── domain/
│   │       │     Ad.java, AdStatus.java, AdRepository.java
│   │       │     AdBalance.java, AdBalanceRepository.java
│   │       │     BalanceTransaction.java, BalanceTransactionRepository.java
│   │       │     TransactionType.java
│   │       └── infrastructure/
│   │             AdJpaRepository.java, AdRepositoryAdapter.java
│   │             AdBalanceJpaRepository.java, AdBalanceRepositoryAdapter.java
│   │             BalanceTransactionJpaRepository.java, BalanceTransactionRepositoryAdapter.java
│   │             ValKeyRotationAdapter.java   ← LPOP/RPUSH, SETNX 재구성  ← 미구현
│   │
│   └── ad-click/                      ← 클릭 집계 도메인
│       └── src/main/java/
│           ├── interfaces/api/
│           │     ClickController.java         (POST /ads/{id}/clicks, GET /ads/{id}/clicks/stats)
│           ├── application/
│           │     ClickFacade.java             ← 어뷰징 체크 → 잔액 차감 → 이벤트 기록 조율
│           │     ClickEventService.java       ← 1단계: 직접 INSERT / 2단계: Kafka publish 교체
│           ├── domain/
│           │     ClickEvent.java
│           │     InvalidReason.java           ← DUPLICATE_IP | DUPLICATE_ANON | RATE_LIMIT
│           └── infrastructure/
│                 ClickJpaRepository.java, ClickRepositoryAdapter.java
│                 AbuseGuardAdapter.java        ← Bucket4j + Valkey TTL
│                 OutboxRepository.java         ← 1단계: 미사용 / 2단계: 활성화
│
└── settings.gradle
```

### 모듈 간 의존 방향

```
ad-api ──► ad-management   (빈 조립, 공통 설정 적용)
ad-api ──► ad-click        (빈 조립, 공통 설정 적용)
ad-click ──► ad-management (BalanceFacade 호출 — 잔액 차감)

의존 방향은 단방향 유지:
ad-management 은 ad-click 을 알지 못함
```

### 레이어별 역할

| 레이어 | 역할 | 의존 가능 대상 |
|--------|------|----------------|
| `interfaces/api/` | Controller, Request DTO | `application` |
| `application/` | Facade: 유스케이스 조율, 트랜잭션 경계 | `domain` Repository 인터페이스만 |
| `application/info/` | Facade 반환 객체 — domain entity 외부 노출 금지 | `domain` |
| `domain/` | Entity, Enum, Repository 인터페이스 | 없음 (순수 Java, JPA 어노테이션 제외) |
| `infrastructure/` | JpaRepository, RepositoryAdapter, Valkey 연동 | `domain` |

**핵심 의존 규칙**
- `application/Facade`는 `domain/Repository` 인터페이스에만 의존 → JPA 구현체 직접 주입 금지
- `infrastructure/RepositoryAdapter`가 `domain/Repository`를 구현 → Spring이 Facade에 주입
- `interfaces/api/`의 Controller는 `Info` 객체를 그대로 응답으로 사용 (별도 변환 불필요)

---

## 11. 시스템 핵심 구간 및 구현 우선순위

이 시스템의 본질은 단 두 줄로 요약됩니다.

> **"클릭 한 번에 10원이 정확히 한 번만 차감되고, 잔액이 0이 되는 순간 광고가 멈춰야 한다."**

이 두 조건을 중심으로 구현 우선순위를 정합니다.

---

### 1순위 — 잔액 차감 정합성 + 동시성 처리

광고주의 실제 돈이 오가는 영역으로, 오류 발생 시 즉각적인 금전 피해로 이어집니다.

```
잔액 초과 차감 → 광고주 금전 피해 → 신뢰 손실, 환불 분쟁
잔액 차감 누락 → 서비스 수익 손실
```

따닥 이슈(동시 클릭) 발생 시 잔액이 음수가 되어선 안 되며,  
트랜잭션 장애 시 "잔액만 차감되고 클릭이 기록 안 됨" 또는 그 반대 상황을 방지해야 합니다.  
클릭 처리 흐름의 트랜잭션 범위 `[4~7]`을 단일 트랜잭션으로 묶은 핵심 이유입니다.

**검증 포인트**
- 동시 클릭 N개 발생 시 잔액이 정확히 N × 10원만 차감되는가
- 트랜잭션 롤백 시 잔액 차감과 클릭 기록이 함께 롤백되는가

---

### 2순위 — 잔액 소진 시 실시간 중단

잔액이 0이 된 이후에도 광고가 노출되면 초과 과금이 발생합니다.  
특히 고TPS 환경에서 "마지막 잔액"을 놓고 동시 클릭이 경쟁하는 상황이 1순위와 맞닿아 있습니다.

```
잔액 10원 남은 상태 + 동시 클릭 10개 → 1개만 유효, 9개는 차단되어야 함
```

**검증 포인트**
- 잔액 소진 시 즉시 `EXHAUSTED` 전환 + Valkey 큐 제거가 원자적으로 처리되는가
- 소진 이후 클릭 요청이 들어왔을 때 `404`로 차단되는가

---

### 3순위 — 어뷰징 방어

어뷰징은 잘못된 과금으로 직결됩니다.  
광고주 입장에서 "내 예산이 왜 이렇게 빨리 소진됐나"가 가장 큰 불만이 됩니다.

**검증 포인트**
- 동일 IP + 동일 광고 60초 내 재클릭이 `is_valid=false`로 기록되는가
- Rate Limit 초과 시 `429` 응답이 반환되는가
- Valkey 장애 시 Fail Open으로 처리되고, 이후 사후 보정 배치가 중복 클릭을 탐지하는가

---

### 4순위 — Round Robin 균등 노출

광고주 간 공정성 문제이지만 1~3순위와 달리 직접적인 금전 오류는 아닙니다.  
Valkey 장애 시 랜덤 선택으로 대체해도 단기적으로 허용 가능한 이유입니다.

**검증 포인트**
- 광고 N개 등록 시 각 광고가 고르게 순환 노출되는가
- Valkey 장애 시 DB Fallback으로 ACTIVE 광고가 정상 반환되는가
- Valkey 재시작 후 SETNX 락으로 단일 서버만 큐를 재구성하는가

---

### 핵심 구간 요약

| 순위 | 구간 | 오류 발생 시 영향 | 핵심 방어 수단 |
|------|------|-------------------|----------------|
| 1 | 잔액 차감 정합성 | 즉각 금전 피해 | Pessimistic Lock, 단일 트랜잭션 |
| 2 | 잔액 소진 즉시 중단 | 초과 과금 | 트랜잭션 내 상태 전환 + Valkey 큐 제거 |
| 3 | 어뷰징 방어 | 부당 과금 | Bucket4j + Valkey TTL + 사후 보정 배치 |
| 4 | Round Robin 균등 노출 | 공정성 문제 | Valkey LPOP/RPUSH + SETNX 재구성 |

---

## 요구사항 충족 매핑

| 요구사항 | 충족 방법 |
|----------|-----------|
| R1 건당 10원 차감 | `BalanceService` 트랜잭션 내 차감 + `balance_transactions` 기록 |
| R2 광고별 독립 잔액 | `ad_balances` 테이블 광고별 분리 관리 |
| R3 잔액 소진 시 자동 중단 | 차감 후 잔액 0 확인 → `EXHAUSTED` 처리 + Valkey 큐 제거 |
| R4 균등 노출 | Valkey Round Robin Queue (`LPOP/RPUSH`) |
| R5 어뷰징 방어 | Bucket4j (Rate Limiting) + Valkey TTL (중복 클릭 무효화) |
| R6 동시성 방어 | 1단계 Pessimistic Lock → 2단계 Valkey Lua Script |
