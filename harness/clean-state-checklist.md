# Clean State Checklist — Ad Click Aggregation

> 세션 종료 전 반드시 이 항목들을 점검하세요.

---

## 1. 빌드 및 실행 확인

- [ ] `./gradlew build` 가 오류 없이 완료된다
- [ ] `./gradlew :apps:ad-api:bootRun` 으로 서버가 정상 기동된다
- [ ] 기동 후 `GET /api/v1/ads/next` 등 기본 엔드포인트가 응답한다

---

## 2. 테스트 확인

- [ ] `./gradlew test` 가 오류 없이 완료된다
- [ ] 이번 세션에서 추가한 테스트가 모두 `passing` 상태다
- [ ] 기존에 통과하던 테스트가 깨지지 않았다 (회귀 없음)

---

## 3. feature_list.json 상태 정합성

- [ ] `in_progress` 상태인 기능이 최대 1개다
- [ ] 이번 세션에서 완료한 기능의 `status` 가 `done` 으로 변경됐다
- [ ] `done` 으로 표시된 기능의 `evidence` 필드가 채워져 있다
- [ ] 실제로 검증되지 않은 기능이 `done` 으로 표시되지 않았다

---

## 4. progress log 업데이트

- [ ] 이번 세션의 Session Record 가 추가됐다
- [ ] `Completed` 항목이 실제로 완료된 것만 기록됐다
- [ ] `Known risks` 에 이번 세션에서 발견한 위험 요소가 기록됐다
- [ ] `Next best action` 이 다음 세션의 명확한 시작점을 가리킨다
- [ ] `Highest priority unfinished feature` 가 현재 상태를 반영한다

> 현재 progress log 파일명은 legacy 호환을 위해 `harness/claude-progress.md`를 유지하지만,
> 내용은 Claude 전용이 아니라 all-agent 진행 이력으로 관리한다.

---

## 5. session-handoff.md 업데이트

- [ ] `Last Updated` 날짜가 오늘 날짜다
- [ ] `Currently Verified` 가 실제 검증된 상태를 반영한다
- [ ] `Changes This Session` 에 이번 세션의 변경 사항이 기록됐다
- [ ] `Still Broken or Unverified` 에 알려진 문제가 기록됐다
- [ ] `Next Best Action` 이 다음 세션에서 바로 실행할 수 있는 수준으로 구체적이다

---

## 6. 미완성 작업 점검

- [ ] 미완성 코드(TODO, FIXME, 임시 주석)가 `feature_list.json` 의 `notes` 또는 Session Record 의 `Known risks` 에 기록됐다
- [ ] 절반만 구현된 기능이 `done` 으로 표시되지 않았다
- [ ] 다음 세션이 수동 수정 없이 `Next Best Action` 만 따라 바로 시작할 수 있다

---

## 7. 핵심 불변 조건 점검

이 시스템의 핵심 불변 조건이 유지되는지 확인합니다.

- [ ] 조회(GET /api/v1/ads/next) 1회에 잔액이 정확히 10원 차감된다
- [ ] 클릭(POST /api/v1/ads/{adId}/clicks) 1회에 잔액이 정확히 50원 차감된다
- [ ] 잔액이 0 미만으로 내려가지 않는다
- [ ] EXHAUSTED 광고는 조회 큐에서 제외되며, 클릭 요청 시 404가 반환된다
- [ ] 중복 클릭(동일 IP/익명ID + 광고, 60초 내)은 잔액을 차감하지 않는다
