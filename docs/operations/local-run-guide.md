# 로컬 실행 및 장애 점검 가이드

## 실행 순서

1. 의존성 컨테이너를 실행한다.

```bash
docker compose up -d
```

2. 스키마를 준비한다.

```bash
docker compose exec -T mysql mysql -uadclick -padclick adclick < docs/schema.sql
```

3. 로컬 seed 데이터를 넣는다.

```bash
docker compose exec -T mysql mysql -uadclick -padclick adclick < docs/seed-mvp1.sql
```

4. API 서버를 실행한다.

```bash
./gradlew :apps:ad-api:bootRun
```

5. Kafka UI를 연다.

```text
http://localhost:8081
```

## 정상 상태 확인

```bash
docker compose ps
curl -s http://localhost:8080/api/v1/ads/next
curl -s 'http://localhost:8080/api/v1/admin/click-event-outbox/failed?size=20'
```

Kafka UI에서 다음 topic을 확인한다.

- `ad-click-events`
- `ad-click-events-dlt`

Consumer group은 `ad-click-aggregation`을 확인한다.
서버 기동 시 topic을 자동 생성하려면 다음 설정을 켠다.

```yaml
adclick:
  kafka:
    topics:
      auto-create-enabled: true
```

운영 Kafka에서 topic auto-create가 비활성화되어 있거나 애플리케이션에 topic 생성 권한이 없다면 두 topic은 배포 전에 미리 생성한다.

## Outbox 장애 점검

Kafka 발행 실패가 반복되면 producer-side DLQ로 `click_event_outbox.status = 'FAILED'` row가 쌓인다.

```sql
SELECT id, topic, message_key, attempt_count, last_error, failed_at
FROM click_event_outbox
WHERE status = 'FAILED'
ORDER BY failed_at DESC
LIMIT 20;
```

운영 API 조회:

```bash
curl -s 'http://localhost:8080/api/v1/admin/click-event-outbox/failed?size=20'
```

Kafka broker 복구와 payload 검토가 끝난 뒤 재발행해도 되는 row만 재처리한다.

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/click-event-outbox/{outboxId}/retry
```

## Consumer DLT 점검

집계 DB 처리 실패가 반복되면 consumer-side DLT topic으로 메시지가 이동한다.

- DLT topic: `ad-click-events-dlt`
- retry 설정: `adclick.kafka.consumer.dlt.*`
- 원본 topic: `ad-click-events`

점검 순서:

1. Kafka UI에서 `ad-click-events-dlt` 메시지를 확인한다.
2. message key와 payload의 `clickEventId`, `adId`, `clickedAt`을 확인한다.
3. exception header로 실패 원인을 확인한다.
4. DB 장애, schema 오류, serialization 오류를 먼저 복구한다.
5. 재처리가 필요하면 DLT 메시지를 원 topic `ad-click-events`로 재발행한다.

집계 consumer는 `processed_click_events.click_event_id`로 멱등 처리하므로 같은 click event가 다시 들어와도 중복 집계는 방지된다.

## 자주 보는 로그

```bash
docker compose logs mysql
docker compose logs kafka
docker compose logs valkey
```

Spring Boot 실행 로그에서 다음 키워드를 확인한다.

- `click event outbox publish failed`
- `stale click event outbox rows recovered`
- `click event batch aggregation failed`

## 로컬 DB 스키마 변경 시

`docs/schema.sql`이 변경된 뒤 기존 로컬 DB 볼륨을 그대로 쓰면 `ddl-auto=validate`가 실패할 수 있다.
로컬 검증 환경에서는 DB를 재생성하거나 변경된 컬럼과 인덱스를 수동 반영한다.
