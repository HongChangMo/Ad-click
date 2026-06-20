# MVP 1 운영 절차

## 실행 기준

- API 서버: `./gradlew :apps:ad-api:bootRun`
- HTTP 포트: `8080`
- MySQL: `localhost:3306`, database/user/password `adclick`
- Valkey 호환 캐시: `localhost:6379`
- Kafka: `localhost:9092`
- Kafka UI: `http://localhost:8081`
- 과금 정책: 조회 `VIEW=10`, 클릭 `CLICK=50`, 환불 `REFUND=50`

## 정상 플로우

1. 광고를 등록한다.
2. 잔액을 충전한다.
3. `GET /api/v1/ads/next`로 노출 광고를 받는다.
4. 노출 시 광고 잔액에서 10원이 차감되고 `VIEW` 거래가 기록된다.
5. `POST /api/v1/ads/{adId}/clicks`로 클릭을 기록한다.
6. 유효 클릭이면 광고 잔액에서 50원이 차감되고 `CLICK` 거래가 기록된다.
7. 잔액이 0원이 되면 광고는 `EXHAUSTED`로 전환되고 rotation queue에서 제거된다.

## 로컬 seed 데이터

`docs/seed-mvp1.sql`은 로컬 검증용 광고 50개를 생성한다.

- ACTIVE 광고 40개
- PAUSED 광고 5개
- EXHAUSTED 광고 5개
- 36-40번 ACTIVE 광고는 잔액 소진 검증용 저잔액 광고

seed 파일은 `TRUNCATE` 후 고정 ID로 데이터를 다시 넣는다. 운영 데이터에는 사용하지 않는다.
기존 로컬 DB 볼륨을 유지한 상태에서 `docs/schema.sql`이 바뀐 경우에는 `ddl-auto=validate`가 실패할 수 있다.
이때는 로컬 DB를 재생성하거나 변경된 컬럼/인덱스를 수동 반영한 뒤 API를 기동한다.

## 어뷰징 방어

- 동일 IP가 같은 광고를 60초 내 재클릭하면 두 번째 클릭은 `DUPLICATE_IP`로 무효 처리된다.
- 동일 `anonymous_id` 쿠키가 같은 광고를 60초 내 재클릭하면 `DUPLICATE_ANON`으로 무효 처리된다.
- IP별 클릭 요청은 기본 60초 100회로 제한되며 초과 시 HTTP 429를 반환한다.
- Valkey 장애 시 중복 방어와 rate limit은 fail-open으로 동작한다.
- Valkey 연산은 Resilience4j retry + circuit breaker로 보호한다.
  - 기본 retry는 최대 2회, 50ms에서 시작해 2배수로 증가하며 최대 200ms를 넘지 않는다.
  - 장애가 반복되어 circuit이 열리면 Redis 호출을 잠시 건너뛰고 기존 fallback 경로를 사용한다.

## Kafka 클릭 이벤트

클릭 이벤트는 DB 저장 트랜잭션 안에서 outbox row로 함께 저장하고, outbox relay가 Kafka topic으로 발행한다.

- topic: `ad-click-events`
- DLT topic: `ad-click-events-dlt`
- producer module: `ad-click`
- consumer module: `ad-aggregation`
- outbox table: `click_event_outbox`
- Kafka publish 실패는 outbox row를 `PENDING`으로 유지하고 `attempt_count`, `last_error`를 갱신한다.
- relay 재시도 때문에 Kafka 이벤트는 중복 발행될 수 있으며, consumer idempotency가 최종 집계 중복 반영을 막는다.
- outbox relay는 짧은 claim 트랜잭션에서 PENDING row를 PROCESSING으로 표시한 뒤 commit하고, DB lock 없이 Kafka send를 일괄 요청한다.
- relay 실패 row는 PENDING으로 되돌아가며 `next_retry_at` 이후 재시도된다. retry 지연은 initial interval, multiplier, max interval 설정을 따른다.
- retry 횟수가 `adclick.kafka.outbox.relay.retry.max-attempts`에 도달하면 row는 `FAILED`로 격리된다. `FAILED` row는 producer-side DLQ로 보고 `last_error`, payload, Kafka broker 상태를 확인한 뒤 수동 재처리 여부를 결정한다.
- relay 장애로 PROCESSING에 남은 row는 `processing-timeout-seconds` 이후 PENDING으로 복구된다.
- producer는 idempotence를 켠다: `enable.idempotence=true`, `acks=all`, `retries=Integer.MAX_VALUE`.
- producer는 `batch-size`, `linger.ms`, `compression-type` 설정으로 broker 전송 효율을 높인다.
- consumer는 batch listener와 manual ack를 사용하고, 한 트랜잭션의 DB batch 처리 성공 후 offset을 acknowledge한다. DB 처리 실패 시 ack하지 않고 예외를 다시 던진다.
- consumer 실패는 `adclick.kafka.consumer.dlt.max-attempts`만큼 처리 시도 후 `adclick.kafka.topics.click-events-dlt` topic으로 격리한다.
- consumer poll batch 크기는 `spring.kafka.consumer.properties.max.poll.records`로 조정한다.
- consumer는 `processed_click_events.click_event_id`를 idempotency key로 사용한다.
- consumer는 `click_daily_stats` 일별 projection을 업데이트한다.

Outbox DLQ 점검 예시:

```sql
SELECT id, topic, message_key, attempt_count, last_error, failed_at
FROM click_event_outbox
WHERE status = 'FAILED'
ORDER BY failed_at DESC
LIMIT 20;
```

운영 API로도 `FAILED` row를 확인할 수 있다.

```bash
curl -s 'http://localhost:8080/api/v1/admin/click-event-outbox/failed?size=20'
```

Kafka broker 복구나 payload 검토가 끝난 뒤 재발행해도 된다고 판단한 row는 `PENDING`으로 되돌린다.

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/click-event-outbox/{outboxId}/retry
```

재처리 API는 `FAILED` 상태인 row만 대상으로 한다. 이미 `PENDING`, `PROCESSING`, `PUBLISHED` 상태인 row나 존재하지 않는 id는 404를 반환한다.

Kafka UI에서 topic과 consumer group을 확인할 수 있다.
Kafka 설정은 `apps/ad-api/src/main/resources/application-kafka.yml`에서 관리한다.
`application.yml`은 `spring.config.import=classpath:application-kafka.yml`로 해당 설정을 불러온다.

Consumer DLT 점검:

1. Kafka UI에서 `ad-click-events-dlt` topic 메시지를 확인한다.
2. DLT 메시지의 key, payload, exception header를 확인한다.
3. 집계 DB 장애나 schema 오류를 먼저 복구한다.
4. 재처리가 필요하면 DLT 메시지를 원 topic `ad-click-events`로 재발행한다.

운영 Kafka에서 topic auto-create가 비활성화되어 있다면 `ad-click-events`와 `ad-click-events-dlt`는 배포 전에 미리 생성한다.

## Valkey 장애 구간 보정

Valkey 장애 중에는 fail-open 정책 때문에 중복 클릭이 유효 클릭으로 저장될 수 있다.
장애가 복구되면 장애 구간의 시작/종료 시각으로 보정 API를 호출한다.

```bash
curl -s -X POST http://localhost:8080/api/v1/clicks/reconciliation \
  -H 'Content-Type: application/json' \
  -d '{"from":"2026-06-20T10:00:00","to":"2026-06-20T10:10:00"}'
```

보정은 같은 `adId + ipAddress` 그룹에서 첫 유효 클릭만 유지하고 이후 클릭을
`DUPLICATE_IP`로 무효화한다. 무효화된 클릭 1건마다 50원을 광고 잔액에 환불하고
`REFUND` 거래를 기록한다.

수동 API와 별도로 보정 runner를 스케줄 실행할 수 있다. 기본값은 비활성화다.

```yaml
adclick:
  click:
    reconciliation:
      runner:
        enabled: true
        fixed-delay-ms: 60000
        window-minutes: 10
        lag-seconds: 30
        lock-ttl-seconds: 300
```

위 설정은 매 60초마다 현재 시각에서 30초 lag를 둔 최근 10분 구간을 보정한다.
수동 API도 동일 runner를 사용하므로 보정 로직의 진입점은 하나로 유지된다.
스케줄 runner는 Valkey TTL lock으로 중복 실행을 방지한다. 기본 TTL은 300초다.
Valkey 장애로 lock 확인이 실패하면 보정 누락을 피하기 위해 fail-open으로 1회 실행한다.

## 점검 명령

```bash
./gradlew clean test
./gradlew :apps:ad-api:bootRun
```

```bash
docker compose ps
docker compose logs mysql
docker compose logs valkey
```

## 현재 한계

- 인증/권한은 아직 없다.
- 스키마 마이그레이션 도구는 아직 없다. 로컬 MVP 1 실행은 `docs/schema.sql`로 준비한다.
- reconciliation 스케줄 runner는 Valkey TTL lock으로 중복 실행을 방지한다. 다만 lock은 Valkey 장애 시 fail-open이므로 강한 exactly-once batch 보장은 아니다.
- Retry/circuit breaker metric/actuator 노출은 아직 없다.
