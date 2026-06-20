# 기술별 책임과 보장 범위

이 문서는 광고 클릭 집계 시스템을 개발하는 사람이 각 기술이 어떤 책임을 갖고,
어떤 보장을 제공하며, 어디까지가 한계인지 빠르게 이해하기 위한 기준 문서다.

## Spring Boot API

광고 API는 외부 클라이언트가 직접 호출하는 HTTP 진입점이다.

- 광고 생성, 조회, 상태 변경을 제공한다.
- 광고 잔액 충전과 잔액 조회를 제공한다.
- 다음 노출 광고 조회를 제공한다.
- 광고 클릭 요청을 받고 클릭 결과를 반환한다.
- 클릭 통계 조회와 장애 구간 보정 API를 제공한다.

API 계층은 요청/응답 계약을 관리하고, 실제 비즈니스 규칙은 각 application facade에 위임한다.
광고 생성, 잔액 차감, 클릭 저장처럼 DB 정합성이 필요한 처리는 MySQL 트랜잭션을 기준으로 처리한다.

## MySQL

MySQL은 시스템의 기준 데이터 저장소다.

- 광고, 광고 잔액, 잔액 거래 내역을 저장한다.
- 원본 클릭 이벤트를 `click_events`에 저장한다.
- Kafka 발행 예정 이벤트를 `click_event_outbox`에 저장한다.
- Kafka consumer가 이미 처리한 클릭 이벤트를 `processed_click_events`에 저장한다.
- 일별 클릭 집계 projection을 `click_daily_stats`에 저장한다.

잔액 차감과 클릭 저장은 같은 요청 트랜잭션 안에서 처리한다. Kafka 발행은 직접 요청 경로에서 수행하지 않고,
같은 트랜잭션 안에 outbox row를 저장한 뒤 relay가 비동기로 발행한다.

## Valkey

Valkey는 빠른 조회와 중복 방어를 위한 보조 저장소다. 기준 데이터 저장소가 아니므로 장애 시 요청을 막지 않고
DB 기반 fallback 또는 fail-open 정책을 사용한다.

- 광고 로테이션 queue를 보관해 다음 광고 조회를 빠르게 처리한다.
- 같은 광고에 대한 60초 내 동일 IP 재클릭을 `DUPLICATE_IP`로 방어한다.
- 같은 광고에 대한 60초 내 동일 anonymous id 재클릭을 `DUPLICATE_ANON`으로 방어한다.
- IP별 클릭 rate limit을 적용해 기본 60초 100회 초과 시 HTTP 429를 반환한다.
- 클릭 보정 scheduler의 중복 실행을 줄이기 위해 TTL lock을 제공한다.

Valkey 호출은 Resilience4j retry와 circuit breaker로 보호한다. 기본 retry는 50ms에서 시작해 2배수로 증가하고
최대 200ms를 넘지 않는다. 장애가 반복되어 circuit이 열리면 Redis 호출을 잠시 건너뛰고 fallback 경로를 사용한다.

Valkey 장애 중 fail-open으로 저장된 중복 클릭은 이후 보정 API 또는 보정 runner가 `click_events`를 기준으로 다시 검사하고,
중복 유효 클릭을 무효화한 뒤 잔액을 환불한다.

## Kafka

Kafka는 클릭 이벤트를 비동기 집계 모듈로 전달하는 이벤트 스트림이다.

- topic: `ad-click-events`
- producer module: `ad-click`
- consumer module: `ad-aggregation`
- local UI: `http://localhost:8081`

클릭 요청 경로는 Kafka broker에 직접 의존하지 않는다. 클릭 이벤트 저장과 함께 `click_event_outbox` row를 저장하고,
outbox relay가 PENDING row를 Kafka로 발행한다. 발행 성공 시 row는 PUBLISHED로 바뀌고, 실패하면 PENDING 상태로 남아
다음 relay 실행에서 재시도된다.

Producer는 idempotence를 켠다.

- `enable.idempotence=true`
- `acks=all`
- `retries=Integer.MAX_VALUE`
- `max.in.flight.requests.per.connection=5`

이 설정은 producer 세션 안에서 broker로 재시도되는 중복을 줄인다. 다만 outbox relay는 at-least-once 발행 구조이므로,
Kafka send 성공 후 PUBLISHED 저장 전에 프로세스가 종료되면 같은 이벤트가 다시 발행될 수 있다.

## Kafka Consumer 멱등성

집계 consumer는 white box idempotency 패턴을 사용한다. 메시지 처리 여부를 외부 offset만 믿지 않고,
업무 DB의 `processed_click_events.click_event_id`에 직접 기록한다.

처리 순서는 다음과 같다.

1. consumer가 `ClickEventMessage`를 수신한다.
2. `processed_click_events`에 `click_event_id`가 이미 있으면 중복 메시지로 보고 집계를 건너뛴다.
3. 없으면 `processed_click_events`에 먼저 저장한다.
4. `click_daily_stats`의 valid/invalid count를 갱신한다.
5. DB 처리가 끝난 뒤 Kafka offset을 manual ack 한다.

`processed_click_events.click_event_id`는 PK이므로 consumer 재시도, relay 재발행, broker redelivery가 발생해도
같은 클릭 이벤트는 한 번만 집계된다.

## 보장 수준

현재 Kafka 흐름은 다음 보장을 목표로 한다.

- 클릭 원본 저장: MySQL 트랜잭션 기준 exactly-once에 가깝게 저장한다.
- Kafka 발행: outbox relay 기준 at-least-once 발행이다.
- 집계 반영: `processed_click_events` idempotency key로 effectively-once 집계 반영을 목표로 한다.
- offset commit: DB 처리 성공 후 manual ack 한다.

Kafka의 exactly-once transaction만으로 DB와 Kafka를 하나의 원자적 트랜잭션으로 묶지는 않는다. 대신 outbox와 consumer
멱등성 조합으로 DB commit 이후 Kafka 발행 누락을 줄이고, 중복 발행이 집계 중복으로 이어지지 않도록 설계한다.

## 테스트 전략

- 단위 테스트는 facade, adapter, relay의 분기와 상태 변경을 검증한다.
- JPA 테스트는 MySQL Testcontainer로 repository와 집계 저장 로직을 검증한다.
- Kafka 통합 테스트는 Embedded Kafka와 MySQL Testcontainer를 함께 사용해 producer-send, consumer-receive,
  DB idempotency, manual ack 이후 집계 projection까지 검증한다.
- API E2E 테스트는 Kafka listener와 outbox relay를 끄고 HTTP 계약과 핵심 DB 흐름을 검증한다.
