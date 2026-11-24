# 러닝 세션 분리 검증 하네스 (k6)

러닝 세션을 메인 서버에서 떼어 내는 근거를 깨 보려는 2차 캠페인의 하네스 설계다. 실제 앱처럼 방 단위로 붙고 끊기는 클라이언트, 시드와 스텁, 장애 스크립트, 무결성 검사, 그리고 각 지표를 어느 파일이 계산하는지를 적는다. 1차 캠페인(`load_test/` 바로 아래, Redis vs RDS)은 고치지 않고 두며, 그 숫자는 run id를 달아 기준선으로만 쓴다 (D-25).

> 상태: 계획 — 하네스 코드는 아직 없다. 지금 있는 것은 이 폴더의 문서 넷과 `scripts/deploy-cadence.sh`, `sql/prod-evidence.sql`뿐이다. 코드를 만드는 PR이 이 문서를 같이 고친다.

| 무엇 | 어디 |
|---|---|
| 왜 재는가 · 비교군 · 시나리오 · 지표의 뜻 | [verification.md](../../docs/msa/running-session-server/verification.md) 「원칙」 「비교군」 「지표」 |
| 기준 숫자 · 실행 규칙 · 판정 방식 | [PREREG.md](PREREG.md) |
| 실행 순서 (0단계 운영 증거부터) | [RUNBOOK.md](RUNBOOK.md) |
| 잰 숫자 | [RESULTS.md](RESULTS.md) |
| 1차 캠페인 · 그 숫자 | [../README.md](../README.md) · [../RESULTS.md](../RESULTS.md) |
| 러닝 WebSocket 계약 | [api-spec.md](../../docs/api-spec.md) 5-C · 5-D |
| 앱 재접속 값 (Flutter) | [runtime.md](../../docs/msa/running-session-server/runtime.md) |

> 주소·키·비밀값은 적지 않는다. JWT 비밀값은 파일에서 읽고 출력하지 않는다.

## 목적 · 구성

- **재는 것** — 메인 배포·장애 때 진행 중 러닝이 이어지는가 (D-45). 같은 부하를 비교군 A · A′ · B0 · B2에 넣고 견준다 — 제안 (D-57).
- **원칙** — 앱과 같은 클라이언트, 실제 ALB 뒤 둘 이상의 인스턴스, 운영에서 뽑은 부하 모양. 회차 규칙은 PREREG 「실행 규칙」에만 둔다.

| 경로 | 내용 | 상태 |
|---|---|---|
| `README.md` · `PREREG.md` · `RUNBOOK.md` · `RESULTS.md` | 설계 · 사전 등록 · 실행 순서 · 결과 | 있음 |
| `scripts/deploy-cadence.sh` | 운영 배포마다 바뀐 파일을 실시간 · 끝내기 · 나머지로 가른다 | 있음 |
| `sql/prod-evidence.sql` | 0단계 운영 증거 — 종료 순간 진행 중 러닝, 슬롯 시간 합계, 대조 기준선 (읽기 전용) | 있음 |
| `k6/lib/` | `runner.js`(소켓 하나), `room.js`(VU 하나 = 방 하나), `rest.js`(REST · 결과 조회 탐침), `sse.js`(스트림 유지), `metrics.js` | 계획 |
| `k6/scenarios/` | 시나리오마다 한 파일 (아래 표) | 계획 |
| `k6/profiles/` | `rates.json`(L30 · L60 · L100 고정 도착률), `baselines.json`(SC0), `rest-mix.json` · `pace.json` · `finish-density.json`(운영 합계에서), `prod-busiest-day.json`(SC13) | 계획 |
| `scripts/` | `k6-run.sh`(digest 고정, 원시 CSV + native histogram), `presets.sh`, `gate.sh`, `arm-jobs.sh`, `deploy-sim.sh`, `weather-mode.sh`, `netem.sh`, `gc-sample.sh`, `lag-prober` | 계획 |
| `chaos/` | 장애 주입 (아래 표) | 계획 |
| `sql/` | `seed-slot.sql`, `seed-display.sql`, `verify-integrity.sql`, `reconcile-results.sql` | 계획 |
| `analyze/` | `windowed.py`(구간별 백분위), `slope.py`(기울기 + 95% 구간), `verify-tracks.py` | 계획 |
| `stub/weather-stub.js` | 고정 지연(기본 2초)과 SC8 분포를 고르는 날씨 스텁 | 계획 |
| `monitoring/` | 대시보드 v2 | 계획 |
| `results/` | 회차별 원시 자료 목록만 — 원시 자료는 S3 (위치 정하지 않음, verification 「정하지 않은 것」) | 계획 |

**옛 하네스에서 가져다 쓰는 것** — `load_test/k6/lib/`의 `token.js`(토큰 서명) · `ids.js`(사용자 · 방 번호) · `track.js`(가상 좌표)를 고치지 않고 불러 쓴다. run_id 규칙과 시작 · 끝내기 분산, Prometheus remote write도 같다 ([../README.md](../README.md) 「식별자 규칙 (시드와 k6가 공유)」).

## 클라이언트 모델

- **VU 하나 = 방 하나 = 소켓 넷** — `k6/websockets`로 한 VU가 소켓 넷을 연다. 상대 진행률 공백 · 전파 지연 · 늦은 DISCONNECTED를 한 시계로 잰다. 한 방은 k6 호스트 하나에만 둔다. 소켓 핸들러 안의 REST는 `http.asyncRequest`로 보내 같은 방 러너를 멈추지 않는다.
- **자리 역할** — 옛 하네스와 같다: 0 · 1 나란히(콤보), 2 중간에 제자리, 3 일시정지 후 재개. 페이스는 `profiles/pace.json`에서 시드로 뽑는다.
- **러너 한 명의 흐름** — 계약은 api-spec 5-C 「WebSocket 연결 — `/api/v1/ws/running`」 · 5-D 「`RUNNING_LOCATION_UPDATE` (C→S) — 위치 정보 전송 (10초 배치)」를 따른다.

| 언제 | 하는 일 |
|---|---|
| 연결 직후 | `RUNNING_START` |
| `RUNNING_STARTED`를 받을 때마다 | 로컬 트랙 전체를 sequence 0부터 다시 보낸다 — `RESEND_CHUNK_POINTS`점씩, 한 메시지가 64KB 한도를 넘지 않게. 멈춘 채였으면 그 뒤 `RUNNING_PAUSE`를 다시 보낸다 |
| 1초마다 · `BATCH_SECONDS`(10)마다 | 점 하나를 쌓는다 · `RUNNING_LOCATION_UPDATE` |
| `HEALTH_CHECK_SECONDS`마다 | `HEALTH_CHECK` |
| `RUNNING_TRACK_UNAVAILABLE` | 위치 배치였으면 다음 배치 차례에 전체를 다시 보내고, 끝내기였으면 `RUNNING_FINISH`를 다시 보낸다 |
| 끝낼 때 | 남은 점을 먼저 보내고 `RUNNING_FINISH`. `FINISH_ACK_TIMEOUT_MS` 안에 ack가 없으면 `FINISH_RETRY_BACKOFF_MS`대로 다시 보내고, `FINISH_GIVE_UP_MS`까지 성공으로 센다. 목표 도달로 먼저 온 `RUNNING_FINISHED`도 ack로 받는다 |
| 예상 못 한 끊김 | `RECONNECT_BACKOFF_MS` 목록대로 다시 붙고 첫 줄로 간다 |

- **앱 값은 환경 변수로만 받는다** — `HEALTH_CHECK_SECONDS` · `RECONNECT_BACKOFF_MS` · `FINISH_ACK_TIMEOUT_MS` · `FINISH_RETRY_BACKOFF_MS` · `FINISH_GIVE_UP_MS` · `RESEND_CHUNK_POINTS`. 앱 값은 정하지 않음 — 노션 프론트 가이드에 있고, [runtime.md](../../docs/msa/running-session-server/runtime.md)의 앱 재접속 표로 옮겨 적는다. 그 전에는 같은 표의 하네스 임시값을 쓰고 회차 색인에 `임시값`이라 적는다. SC1 · SC2 · SC3 측정 회차는 앱 값이 올 때까지 돌리지 않는다(verification 「정하지 않은 것」). 어떤 클라이언트 제한도 서버 제한과 같게 두지 않는다 — PREREG 「실행 규칙」.
- **앱 버전 헤더** — `APP_PLATFORM` · `APP_VERSION`을 환경 변수로 받는다. 대상 DB `app_versions`의 최소 버전 이상이어야 한다.
- **닫힘 분류** — 모든 닫힘을 셋 중 하나로 센다. 서버 코드는 태그로 그대로 남긴다(api-spec 5-C).

| 분류 | 조건 | 셈 |
|---|---|---|
| finished | `RUNNING_FINISHED`를 받은 뒤 하네스가 닫음 | 정상 |
| intended | 자리 3의 재연결처럼 하네스가 계획대로 닫음 | 정상 |
| unexpected | 그 밖의 전부 — `1001` · `1006` · `1009` · `1011` · `4001` · `4500` … | `ws_unexpected_close{code}` |

- **`4500`은 둘로 가른다** — 지금은 전송 한도 초과뿐이지만, A′부터는 계획 종료도 `4500`으로 닫는다 (D-42). 닫힘 이유 문자열로 가르고 `reason` 태그를 단다.
- **RUNNER_DONE** — 러너가 끝나면 JSON 한 줄을 남긴다: run_id · 방 · 자리 · 보낸 마지막 sequence · ack 여부 · 재연결 수 · 다시 보낸 점 수 · 닫힘 분류별 수. 무결성 검사와 트랙 검사가 이 줄을 DB · S3와 맞춘다. 사용자는 시드가 만든 합성 사용자뿐이다.
- **REST · SSE** — 같은 회차에 REST를 도착률 고정(열린 모델)으로 보낸다. 요청마다 `api_group` 태그를 달고, 섞는 비율은 0단계에서 뽑은 18~23시 비율(`profiles/rest-mix.json`)이다. 부하 L30 · L60 · L100은 SC0에서 잰 REST_CAP(A)의 배수로 한 번 얼리고 모든 비교군에 같은 절대값을 넣는다 (verification 「번호」). SSE는 앱 스트림(`/app/stream`)을 잡고만 있는 연결을 `SSE_HOLDERS`개 둔다 — 수는 시나리오마다 verification의 SSE 수.
- **결과 조회 탐침** — ack 직후 6-1을 한 번 GET하고, 기록이 7-1에 보일 때까지 짧게 다시 묻는다 — 끝낸 직후 결과는 세션이, 7-1은 메인이 반영한 뒤 보인다 (D-58).

## 시드 · 스텁

| 무엇 | 파일 | 하는 일 |
|---|---|---|
| 슬롯 방 | `sql/seed-slot.sql` | 시작 시각이 지난 4인 MATCHED 방을 만든다 — 옛 `sql/seed.sql`의 번호 규칙을 쓴다. 트랙을 미리 채우는 만큼 시작 시각을 앞당긴다 |
| 미리 채운 트랙 | `k6/lib/runner.js` · `scripts/arm-jobs.sh` | 기본은 프로토콜로 채운다 — 시작 직후 미리 만든 점을 묶음으로 보내 서버 경로를 그대로 탄다. 긴 트랙이 많이 필요한 SC9는 `RunningTrackRedisAdapter`가 쓰는 모양(스트림 + 받은 sequence 비트맵)으로 Redis에 바로 넣는다 — 이 모양은 옛 엔진 키라 B2에서는 세션 키로 바꾼다. 제안 |
| 예약 작업 | `scripts/arm-jobs.sh` | `scheduled_jobs`에 `RUNNING_START` · `RUNNING_FORCE_FINISH`를 원하는 시각으로 건다 — SC5의 같은 초 시작, SC7의 같은 초 강제 종료 |
| 표시용 데이터 | `sql/seed-display.sql` | 기록 · 기록 카드 · 알림 · 친구를 가진 표시용 사용자 — REST 읽기가 운영 크기의 데이터를 읽게 한다. 러너와 겹치지 않아 끝내기 INSERT가 읽기 대상을 바꾸지 않는다 |
| 앱 버전 | 시드 | `app_versions`에 헤더와 맞는 행을 넣는다 — 행이 없으면 핸드셰이크가 500, 최소 버전이 더 높으면 403이다 |
| 날씨 | `stub/weather-stub.js` | 기본은 고정 2초, SC8만 분포 — 값은 PREREG 「실행 규칙」 9. C5는 `weather-mode.sh`로 응답을 멈춘다 |
| 넘기기 이벤트 | 시드 | 넘기기 방식이 정해지면 B2 회차에서 그 이벤트도 낸다 — 넘기기-동기 · 넘기기-사본 둘 다 ([architecture.md](../../docs/msa/running-session-server/architecture.md) 「넘기기」) |
| S3 | — | test 버킷에 회차 접두어로 쓰고 회차 뒤 지운다 (RUNBOOK 「3. 상태 초기화」) |

## 시나리오 · 장애 스크립트

시나리오의 뜻과 비교군은 verification 「핵심 시나리오 SC1–SC7」 「보조 시나리오 SC0 · SC8–SC14」, 장애 C1–C7은 SC3 안의 번호다. 파일 이름은 계획이다.

| 시나리오 | k6 파일 | 같이 도는 스크립트 |
|---|---|---|
| SC0 보정 · 빈 실험 | `sc0-calibrate.js` | `gate.sh` |
| SC1 러닝 중 메인 배포 · SC2 세션 배포 | `sc1-main-deploy.js` · `sc2-session-deploy.js` | `deploy-sim.sh` — 운영과 같은 instance refresh(`backend-cd-prod.yml`의 설정), ASG가 없으면 같은 순서(새 인스턴스 → 대상 등록 → 옛 인스턴스 종료) |
| SC3 메인 장애 | `sc3-main-fault.js` | `chaos/toxiproxy.sh`(C1 블랙홀 · C2 지연), `chaos/kafka-throwaway.sh`(C3), `chaos/ai-stop.sh`(C4), `weather-mode.sh`(C5), `chaos/s3-deny.sh`(C6), `chaos/main-kill.sh`(C7) |
| SC4 Redis 장애 | `sc4-redis-fault.js` | `chaos/redis-kill.sh`(kill -9) · `chaos/redis-stop.sh`(60초 정지) — Redis마다 따로 |
| SC5 슬롯 시작 폭주 | `sc5-start-storm.js` | `arm-jobs.sh` |
| SC6 결과 복제 | `sc6-results.js` | `lag-prober`(전용 커넥터 · 슬롯), `chaos/kafka-restart.sh` · `chaos/connect-kill.sh` · `chaos/poison-inject.sh`, `sql/reconcile-results.sql` |
| SC7 계정 탈퇴 saga | `sc7-delete-saga.js` | `arm-jobs.sh`(같은 초 강제 종료) |
| SC8 실제 슬롯 한 시간 | `sc8-slot-hour.js` | — |
| SC9 끝내기 몰림 + CPU 원인 | `sc9-finish-burst.js` | 날씨 스텁 고정 2초, `gc-sample.sh`, JFR |
| SC10 러닝 중 REST 급증 | `sc10-rest-spike.js` | — |
| SC11 연결 한계 | `sc11-ceiling.js` | `netem.sh`(느린 클라이언트), `gc-sample.sh`, `analyze/slope.py` |
| SC12 쓰기 경합 | `sc12-write-contention.js` | — |
| SC13 규모 재현 | `sc13-replay.js` | `profiles/prod-busiest-day.json` |

- **Kafka 장애는 버리는 단일 브로커에서만** — 공유 dev/test 클러스터에는 넣지 않는다 (RUNBOOK 「4. 장애 주입」).
- **toxiproxy는 장애 없는 회차에도 경로에 둔다** — 장애 회차만 경로가 달라지지 않게 한다.

## 무결성 검사

| 파일 | 확인하는 것 |
|---|---|
| `sql/verify-integrity.sql` | 유효 러너마다 기록 한 건, 구간 수 = 거리 ÷ 10m, 방이 닫혔다, `RUNNING`으로 남은 참가자 없음, ack를 받은 러너(RUNNER_DONE)마다 기록이 있다(`ack_without_record` = 0, I1) |
| `analyze/verify-tracks.py` | S3 원본 트랙이 RUNNER_DONE이 보낸 sequence를 모두 담았다 |
| `sql/reconcile-results.sql` | B2만 — 러닝 DB 기록 = 메인 사본(누락 0, I5), 중복 반영 · 건너뜀 · DLT 수 ([consistency.md](../../docs/msa/running-session-server/consistency.md) 「대조」) |

검사를 통과하지 못한 회차는 성능 숫자로 쓰지 않고 RESULTS 「무결성」에 기능 결함으로 적는다.

## 지표가 계산되는 곳

지표의 뜻은 verification 「지표」, 서버 지표 이름은 [metrics-convention.md](../../docs/metrics-convention.md)에 있다. 여기는 계산하는 파일만 둔다.

| 지표 | 계산하는 파일 | 원자료 |
|---|---|---|
| `ws_unexpected_close{code}` · `handshake_status{code}` · `reconnect_to_started_ms` · `reconnect_success` · `resend_points` · `resend_duration_ms` · `finish_success` · `finish_latency_to_ack` · 시작 시각 전 거부 비율 | `k6/lib/runner.js` | 소켓 이벤트 |
| `partner_progress_gap_s` · `combo_broken_by_disconnect` · `false_disconnected_broadcast` | `k6/lib/room.js` | 같은 VU 안 네 소켓 — 한 시계 |
| 그룹별 REST 지연 · 오류, health 탐침 성공, `results_first_get_not_found`, `ack_to_main_visible_ms` · `reapply_wait_ms`(B2) | `k6/lib/rest.js` | REST 응답 |
| SSE 재연결 | `k6/lib/sse.js` | 스트림 이벤트 |
| 구간(이전 · 폭주 · 이후)별 백분위, 비율 | `analyze/windowed.py` | 원시 CSV |
| 소켓당 힙 기울기 | `analyze/slope.py` | `gc-sample.sh` + 연결 수 |
| `track_gap` (`track_gap_s` · 완전도) | `analyze/verify-tracks.py` | S3 트랙 + RUNNER_DONE |
| `ack_without_record` | `sql/verify-integrity.sql` | DB + RUNNER_DONE |
| `replication_lag_ms` · `reconcile_missing` (B2) | `sql/reconcile-results.sql` | 러닝 DB · 메인 사본 |
| 커밋 → 브로커 → 받기 지연 (B2 전) | `lag-prober` | 전용 커넥터 · 슬롯 |
| 회차 유효성 — k6 CPU · 시계 차 · CPU 크레딧 · `dropped_iterations` | `scripts/gate.sh` | 호스트 · CloudWatch · k6 |
| 서버 지표(연결 게이지 · 닫힘 코드 · 끝내기 단계 · 잠금 대기 · 커넥션 대기 …) | 앱 `/actuator/prometheus` · redis_exporter · node_exporter · CloudWatch | 대시보드 v2 |

## 옛 하네스와 다른 점

| 항목 | 옛 하네스 (`load_test/k6/running.js`, 10-15 측정) | v2 |
|---|---|---|
| 범위 | WebSocket만 — REST · SSE 없음 | WebSocket + REST(열린 모델) + SSE |
| 대상 | 인스턴스 한 대에 직접 (`k6-run.sh`의 `ws://<앱>:8080`), ALB 없음 | ALB 뒤 둘 이상, TLS |
| 부하 모델 | `per-vu-iterations` — 닫힌 모델 | REST는 도착률 고정. WebSocket은 기기 하나 = 연결 하나라 닫힌 모델 유지 |
| VU | 러너 한 명 | 방 하나(소켓 넷) |
| 재연결 뒤 재전송 | 마지막 배치만 | 로컬 트랙 전체, 64KB 안 묶음 |
| `HEALTH_CHECK` | 보내지 않음 | 앱 주기대로 |
| 끝내기 제한 시간 | 30초(`FINISH_TIMEOUT_MS`) — 앱 Hikari 기본 `connectionTimeout`(30초)과 같아 실패와 늦은 확정을 가를 수 없었다 ([../RESULTS.md](../RESULTS.md) 「정정 (11-24)」) | 서버 제한과 다른 값 + 재시도 (PREREG 「실행 규칙」) |
| 날씨 스텁 | 지연 기본 0 (`WEATHER_STUB_DELAY_MS`) | 고정 2초(기본) · SC8 분포 (PREREG 규칙 9) |
| 앱 버전 헤더 | `ANDROID 1.1.0` 고정 — 지금 운영 최소 버전 1.2.0보다 낮다 | 환경 변수 |
| k6 이미지 | `grafana/k6:latest` | digest 고정 |
| 결과 | 테스트 전체 요약(`--summary-export`), remote write는 p50 · p99 · max만 | 원시 CSV + native histogram, 구간별 백분위 |
| 반복 | 시나리오마다 1회 | n ≥ 3 + 버리는 워밍업 (PREREG) |
| 국면 | run 14에서 동시 시작과 동시 종료가 겹쳤다 | 시작과 끝내기 국면을 섞지 않는다 |
| 느린 클라이언트 | 재지 않음 — 서버는 소켓마다 전송 한도(`running-websocket.send-time-limit` · `send-buffer-size-limit`)를 둔다 | SC11 `netem.sh` |
| 대시보드 | HTTP · Tomcat 스레드 · WS 연결 게이지 · 실행기 · Redis 서버 · CPU 크레딧 · RDS PI · ALB 칸 없음 | 대시보드 v2 |

## 로컬 smoke

- **보는 것** — 스크립트가 끝까지 돌고, 무결성 검사 · 태그 · 닫힘 분류가 맞는지뿐이다. 로컬 숫자는 쓰지 않는다.
- **스택** — `e2e_test/compose.yml` 위에 앱 한 대를 더 띄우고(스키마를 만들지 않게 `JPA_DDL=none`), 그 앞에 ALB 대역 프록시(nginx 또는 haproxy)와 toxiproxy, v2 날씨 스텁을 둔다. 옛 `smoke-local.sh`처럼 compose 파일을 겹쳐 쓴다.
- **같이 돌리는 것** — 같은 스택에서 `MultiInstanceE2eTest`를 `E2E_SECOND_BASE_URL`과 함께 돌린다.
- **명령** — 하네스를 만드는 PR에서 여기에 적는다.

## 정하지 않은 것

1. **앱 재접속 값** — [runtime.md](../../docs/msa/running-session-server/runtime.md).
2. **원시 자료 자리** — verification 「정하지 않은 것」 2.
3. **SC9 트랙을 Redis에 바로 넣을지** — 「시드 · 스텁」 미리 채운 트랙, 제안.
