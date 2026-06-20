# Ad Click Aggregation

광고 노출/클릭 이벤트를 기록하고 광고 잔액을 과금하는 Spring Boot 기반 MVP입니다.

## 구성

- Java 21
- Spring Boot 3.5
- Gradle multi-module
- MySQL 8.0
- Valkey 호환 캐시

모듈 의존 방향은 `ad-api -> ad-management`, `ad-api -> ad-click`,
`ad-click -> ad-management`입니다. `ad-management -> ad-click` 역방향 의존은 금지합니다.

## 로컬 실행

1. 의존성 컨테이너를 실행합니다.

```bash
docker compose up -d
```

2. MySQL 스키마를 준비합니다.

```bash
docker compose exec -T mysql mysql -uadclick -padclick adclick < docs/schema.sql
```

3. 데모용 seed 데이터를 넣습니다. 이 단계는 기존 데이터를 비우고 광고 50개를 다시 생성합니다.

```bash
docker compose exec -T mysql mysql -uadclick -padclick adclick < docs/seed-mvp1.sql
```

4. API 서버를 실행합니다.

```bash
./gradlew :apps:ad-api:bootRun
```

서버는 `http://localhost:8080`에서 실행됩니다.
Kafka UI는 `http://localhost:8081`에서 확인할 수 있습니다.

## 테스트

```bash
./gradlew test
```

현재 MVP 1 기준 전체 테스트는 77개입니다.

## API 예시

광고 등록:

```bash
curl -s -X POST http://localhost:8080/api/v1/ads \
  -H 'Content-Type: application/json' \
  -d '{"advertiserId":1,"name":"spring sale"}'
```

잔액 충전:

```bash
curl -s -X POST http://localhost:8080/api/v1/ads/1/balance/charge \
  -H 'Content-Type: application/json' \
  -d '{"amount":1000}'
```

다음 광고 조회 및 조회 과금:

```bash
curl -s http://localhost:8080/api/v1/ads/next
```

클릭 기록:

```bash
curl -i -X POST http://localhost:8080/api/v1/ads/1/clicks \
  -H 'X-Forwarded-For: 203.0.113.10'
```

클릭 통계 조회:

```bash
curl -s 'http://localhost:8080/api/v1/ads/1/clicks/stats?from=2026-06-20T00:00:00&to=2026-06-21T00:00:00'
```

장애 구간 클릭 보정:

```bash
curl -s -X POST http://localhost:8080/api/v1/clicks/reconciliation \
  -H 'Content-Type: application/json' \
  -d '{"from":"2026-06-20T10:00:00","to":"2026-06-20T10:10:00"}'
```

보정 runner를 주기 실행하려면 `adclick.click.reconciliation.runner.enabled=true`로 설정합니다.
기본값은 false입니다. 스케줄 runner는 Valkey TTL lock으로 중복 실행을 줄입니다.

Kafka topic:

- `ad-click-events`: 클릭 이벤트 발행 topic
- `click_event_outbox`: 클릭 저장 트랜잭션과 함께 저장되는 Kafka 발행 예정 이벤트
- `processed_click_events`: consumer idempotency table
- `click_daily_stats`: Kafka consumer가 업데이트하는 일별 집계 projection
- outbox relay는 기본 활성화되어 있으며 `adclick.kafka.outbox.relay.fixed-delay-ms`와
  `adclick.kafka.outbox.relay.publish-timeout-ms`로 주기와 발행 대기 시간을 조정한다.

Kafka UI:

- URL: `http://localhost:8081`
- cluster name: `local`

## 운영 문서

- [기술별 책임과 보장 범위](docs/architecture/technology-responsibilities.md)
- [MVP 1 운영 절차](docs/operations/mvp1-runbook.md)
- [로컬 스키마](docs/schema.sql)
- [MVP 1 seed 데이터](docs/seed-mvp1.sql)
- [에이전트 작업 규칙](AGENTS.md)
