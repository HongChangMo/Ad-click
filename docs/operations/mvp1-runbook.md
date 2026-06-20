# MVP 1 운영 절차

## 실행 기준

- API 서버: `./gradlew :apps:ad-api:bootRun`
- HTTP 포트: `8080`
- MySQL: `localhost:3306`, database/user/password `adclick`
- Valkey 호환 캐시: `localhost:6379`
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

## 어뷰징 방어

- 동일 IP가 같은 광고를 60초 내 재클릭하면 두 번째 클릭은 `DUPLICATE_IP`로 무효 처리된다.
- 동일 `anonymous_id` 쿠키가 같은 광고를 60초 내 재클릭하면 `DUPLICATE_ANON`으로 무효 처리된다.
- IP별 클릭 요청은 기본 60초 100회로 제한되며 초과 시 HTTP 429를 반환한다.
- Valkey 장애 시 중복 방어와 rate limit은 fail-open으로 동작한다.
- Valkey 연산은 Resilience4j retry + circuit breaker로 보호한다.
  - 기본 retry는 최대 2회, 50ms에서 시작해 2배수로 증가하며 최대 200ms를 넘지 않는다.
  - 장애가 반복되어 circuit이 열리면 Redis 호출을 잠시 건너뛰고 기존 fallback 경로를 사용한다.

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
- reconciliation은 HTTP 수동 트리거 방식이다. 전용 batch runner는 MVP 2 후보이다.
- Retry/circuit breaker metric/actuator 노출은 아직 없다.
