# Metrics Convention

메트릭 이름·태그·계측 종류·기록 위치 규칙 — 메트릭을 새로 만들거나 고칠 때 모두 이 규칙을 따른다.

## 수집 구조

- 앱은 Micrometer로 기록하고 `/actuator/prometheus`로 노출한다. Prometheus가 주기적으로 긁어 가고(pull), Grafana가 Prometheus를 조회한다.
- 앱은 누적값만 들고 있다. Prometheus가 읽어 가도 지워지지 않고, 앱이 재시작할 때만 0으로 돌아간다. 시각별 기록은 Prometheus에 쌓이고, 증가 속도(`rate()`)는 Grafana에서 계산한다.
- 모든 메트릭에 공통 태그 `application=running-service`를 붙인다.

## 로그와 메트릭의 역할

| | 로그 | 메트릭 |
|---|---|---|
| 답하는 질문 | 누가, 왜 실패했나 | 얼마나 많이, 얼마나 빨리 |
| 식별자 | `userId`·`roomId`로 한 건을 찾는다 | 식별자를 쓰지 않는다 — 합계와 분포만 본다 |

- 알림은 메트릭으로 울리고, 원인은 로그로 찾는다. 알림 → 같은 시간대의 ERROR 로그 → requestId 순서로 추적한다.

## 이름

소문자와 점(`.`)으로 쓰고 `runiverse.`로 시작한다. 두 갈래가 있다.

### 진입점 메트릭

```
runiverse.<프로토콜>.<대상>
```

- 요청·메시지가 들어오는 입구마다 하나씩 두고, 성공·실패를 모두 센다. 기능별 구분은 태그(`domain`·`uri`·`event`)로 한다.
- `<대상>`은 세는 것을 복수 명사로 쓴다 — 같은 프로토콜의 다른 메트릭(`runiverse.websocket.connections`)과 이름이 겹치지 않게 한다.

| 메트릭 | 세는 것 |
|---|---|
| `runiverse.http.requests` | 처리한 HTTP 요청 |
| `runiverse.websocket.messages` | 받은 WebSocket 메시지 |
| `runiverse.sse.events` | 보낸 SSE 이벤트 |
| `runiverse.kafka.messages` | 받은 Kafka 메시지 (예정) |

### 핸들러 메트릭

```
runiverse.<도메인>.<기능 폴더>.<동작>
```

- 진입점 메트릭의 태그 조합으로 구할 수 없는 것만 만든다 — URL 템플릿에 가려진 값, 응답에 드러나지 않는 업무 결과(매칭이 기존 방에 합류했는지 새 방을 열었는지), 핸들러 안 특정 구간의 소요 시간.
- `<도메인>`은 `application/` 아래 패키지, `<기능 폴더>`는 그 아래 `command/`·`query/`의 기능 패키지다.
- `<동작>`은 핸들러 이름의 동사를 소문자로 쓴다(`OpenMatchStreamHandler` → `open`). 동사가 기능 폴더 이름과 같으면 생략한다(`signup/SignUpHandler` → `runiverse.auth.signup`).
- 클래스 이름을 그대로 쓰지 않는다 — 클래스 이름이 바뀌면 새 시계열이 생겨 이전 기록·대시보드·알림이 끊긴다.

### 공통

- Prometheus로 나갈 때 `.`이 `_`로 바뀌고 종류별 접미사가 붙는다(`runiverse_http_requests_total`).
- 단위를 이름에 넣지 않는다 — Timer는 초, 나머지는 `baseUnit`으로 지정한다.
- 결과를 이름으로 가르지 않는다(`...requests.success` ✗) — 태그 `result`로 가른다.

## 태그

**값의 종류가 정해진 것만 태그로 쓴다.** 태그 값 조합마다 시계열이 하나씩 생긴다 — 사용자 수만큼 늘어나는 값을 넣으면 Prometheus 메모리가 버티지 못한다.

| 쓴다 | 쓰지 않는다 |
|---|---|
| `domain=auth\|user\|relationship\|match\|running\|scheduling\|notification\|app\|device\|common` | `userId`, `roomId`, `requestId`, `scheduledJobId` |
| `result=success\|failure` | 이메일, 좌표, 예외 메시지 |
| `reason=<아래 목록>` | 요청 경로 원문 — 템플릿(`/users/{userId}/profile`)만 |
| `decision=<메트릭별 목록>` — 성공·실패가 아닌 업무 판정의 갈래 | |
| `filter=pause\|accuracy\|spike\|gap\|stop` — 트랙 필터 단계 | |
| `store=redis\|db` — 러닝 상태를 둔 저장소 | |
| `target=<메트릭별 목록>` — 접근한 상태의 종류 | |
| `phase=running\|finish` — 러닝 중 배치마다 도는 접근인지, 종료 때 한 번 도는 접근인지 | |
| `operation=read\|write` | |
| `code=<메트릭별 목록>` — WebSocket 닫기 코드, 목록 밖은 `unknown` | |
| `stage=<메트릭별 목록>` — 연결이 어느 단계까지 왔는지 | |
| `step=<메트릭별 목록>` — 한 처리 안의 단계 | |
| `trigger=<메트릭별 목록>` — 처리를 일으킨 쪽 | |

- `decision`은 `result`와 섞어 쓰지 않는다. `result`는 처리가 성공했는지, `decision`은 성공한 처리가 어느 쪽으로 판정했는지다. 값 목록은 메트릭마다 아래 직접 만드는 것 표에 적는다.

- **같은 이름의 메트릭은 태그 key 구성이 항상 같아야 한다** — Prometheus 레지스트리가 강제한다. 해당 없는 값도 비우지 않고 정해진 값(`none`·`unknown`)을 넣는다.
- 태그 key는 camelCase, 값은 소문자로 쓴다. `reason`만 코드 이름을 그대로 쓴다.
- 사용자 입력을 태그 값으로 그대로 쓰지 않는다 — 지원하지 않는 event·provider처럼 목록 밖의 값은 `unknown`으로 바꾼다.

### domain

- 요청·메시지를 처리한 코드의 기능 패키지 이름이다. 로그 도메인 태그와 같은 값 집합이다(`[인증]` ↔ `auth`).
- HTTP 요청은 요청에 매핑된 컨트롤러의 패키지다(`/users/me/status` → `user`, `/users/me/running-records` → `running`). 엔드포인트별로 보려면 `uri`·`method`로 거른다.
- 처리할 곳에 닿기 전에 끝난 요청(없는 경로, 보안 필터 거절)은 `common`이다.

### result · reason

| 상황 | result | reason |
|---|---|---|
| 성공 | `success` | `none` |
| 업무 실패 | `failure` | `ErrorCode` 이름(`EMAIL_ALREADY_EXISTS`) |
| 요청 형식·검증 실패 | `failure` | `INVALID_REQUEST`, `MALFORMED_REQUEST_BODY` |
| 없는 경로 | `failure` | `NOT_FOUND` |
| 인증·인가 거절 | `failure` | 해당 코드 이름 |
| 예상 못 한 서버 오류 | `failure` | `INTERNAL_SERVER_ERROR` |
| 실패했는데 원인이 남지 않음 | `failure` | `UNKNOWN` — 원인을 남기지 않는 예외 경로가 생겼다는 신호다. 보이면 코드를 고친다 |

- HTTP는 응답 상태가 4xx·5xx면 `failure`다.
- 응답에서 500으로 숨기는 코드(`USER_NOT_FOUND`)도 `reason`에는 실제 코드를 쓴다 — 운영자는 진짜 원인을 봐야 한다.
- 원인을 코드보다 잘게 나누지 않는다 — 더 자세한 원인은 로그에 있다.

### 프로토콜별 태그

| 메트릭 | 태그 |
|---|---|
| `runiverse.http.requests` | `domain`, `method`, `uri`, `result`, `reason` |
| `runiverse.websocket.messages` | `domain`, `event`, `result`, `reason` |
| `runiverse.sse.events` | `domain`, `event`, `result`, `reason` |
| `runiverse.kafka.messages` | `domain`, `event`, `result`, `reason` |
| `runiverse.websocket.closes` | `domain`, `code` |
| `runiverse.websocket.connections` | `domain`, `stage` |
| `runiverse.sse.connections` | `domain` |

- `uri`는 경로 템플릿이다. 매칭된 템플릿이 없으면(없는 경로, 보안 필터 거절) `UNKNOWN`이다.

## 계측 종류

| 종류 | 쓰는 곳 | 예 |
|---|---|---|
| Counter | 일어난 횟수 | 요청·메시지 수, 외부 API 실패 |
| Timer | 걸린 시간 + 횟수 | 외부 API 호출, 러닝 종료 처리 |
| Gauge | 지금 이 순간의 값 | WebSocket·SSE 연결 수 |
| DistributionSummary | 시간이 아닌 값의 분포 | 러닝 거리 |

- 횟수와 시간을 함께 보려면 Counter를 따로 두지 않고 Timer 하나로 쓴다 — Timer가 횟수도 센다.

## 기록 위치

- **진입점 메트릭:** 레이어를 import하지 않는 공통 장치는 `observability/metrics/`에 둔다. `reason`은 예외를 처리한 presentation(중앙 예외 핸들러, 인증 진입점)이 요청 속성에 남기고, `observability`가 그 값을 읽어 센다 — 속성 key 상수는 `observability`가 소유한다.
- **핸들러 메트릭:** application은 Micrometer를 import하지 않는다. `port/out`의 기록 포트를 호출하고, infrastructure가 `MeterRegistry`로 구현한다.
- **외부 시스템 호출 시간·실패:** infrastructure 어댑터가 직접 기록한다.
- **러닝 상태 저장소 접근:** 어댑터가 `RunningStateMeter`를 거쳐 기록한다. 포트 호출 1번이 1건이다 — 저장소 명령 수로 세면 Redis의 HSET+EXPIRE처럼 DB에선 한 번일 일이 두 번으로 잡혀 저장소끼리 비교할 수 없다. 저장소를 바꿔 끼운 어댑터도 같은 이름·태그로 `store`만 달리 기록한다.

## 기본 제공과 직접 만드는 것

| 기본 제공 | 내용 |
|---|---|
| `http.server.requests` | 엔드포인트별 **응답 시간**·상태 코드 |
| `jvm.*`, `process.*` | 메모리·GC·스레드·CPU |
| `hikaricp.*` | DB 커넥션 풀 — `pool=main`(메인 서버 풀, `spring.datasource.hikari.pool-name`). 풀이 바닥났는지는 `pending` · `timeout`, 오래 쥐는지는 `usage`(누수 감지 10초는 로그로) |
| `lettuce.*` | Redis 명령 |
| `logback.events` | 레벨별 로그 수 — ERROR 증가 알림에 쓴다 |

- 기본 제공으로 나오는 것은 직접 만들지 않는다. 예외: `runiverse.http.requests`는 `http.server.requests`와 요청 수가 겹치지만 `domain`·`reason`을 붙이려고 따로 센다. 응답 시간은 `http.server.requests`로 본다.

직접 만드는 것은 이 표에 먼저 추가하고 나서 만든다.

| 이름 | 종류 | 기록 위치 | 상태 |
|---|---|---|---|
| `runiverse.http.requests` | Counter | `observability/metrics/` | 사용 중 |
| `runiverse.auth.oauthlogin` | Counter | `OauthLoginHandler` → `AuthMetricAdapter` | 사용 중 — `provider=google\|kakao\|apple\|unknown`, `result`, `reason` |
| `runiverse.websocket.messages` | Counter | 러닝 WebSocket 핸들러 | 예정 |
| `runiverse.sse.events` | Counter | 매칭 스트림 연결 | 예정 |
| `runiverse.running.finish.filtered` | DistributionSummary (baseUnit `meters`) | `RunningFinishSteps`(판정) → `RunningMetricAdapter` | 사용 중 — `filter=pause\|accuracy\|spike\|gap\|stop`. 종료 확정 1건당(기록이 생기지 않는 종료 포함) 트랙 필터 단계별로 뺀 거리(feature-spec 트랙 필터). 0도 기록한다. 히스토그램을 켠다(1~10,000m). 판정값 조정용 |
| `runiverse.running.finish.gaps` | Counter | `RunningFinishSteps`(판정) → `RunningMetricAdapter` | 사용 중 — `decision=accepted\|rejected`. 관측 안 된 칸의 인정·거부 수 |
| `runiverse.running.location.goal` | Counter | `RunningFinishSteps`(자동 종료 판정) → `RunningMetricAdapter` | 사용 중 — `decision=finished\|pending`. 러닝 중 누적이 목표를 넘은 배치에서 확정 거리로 끝냈는지, 미달이라 미뤘는지 |
| `runiverse.running.finish.goalpending` | DistributionSummary (baseUnit `meters`) | `RunningFinishSteps`(`forced=false` 종료 판정) → `RunningMetricAdapter` | 사용 중 — `forced=false` 종료를 미뤘을 때의 남은 거리(`RUNNING_GOAL_PENDING`의 `remainingMeters`). 화면 거리와 서버 확정 거리가 얼마나 어긋나는지 본다. 히스토그램을 켠다(1~10,000m) |
| `runiverse.running.finish.inprogress` | Counter | `RunningFinisher` → `RunningMetricAdapter` | 사용 중 — `trigger=user\|goal`. 끝내기 임대를 잡지 못한 종료 수 — 다른 연결이 같은 러닝을 끝내는 중이다(D-82). 사용자 종료는 `RUNNING_TRACK_UNAVAILABLE`로 답하므로 저장소 장애와 이 값으로 가른다 |
| `runiverse.running.room.dropped` | Counter | `RunningRoomMessageDropHandler`(러닝 방 채널 실행기의 거절 처리) | 사용 중 — 태그 없음. 러닝 방 채널(`running:room:*`) 대기 줄이 꽉 차 버린 메시지 수(진행 · 콤보 · supersede를 가르지 않는다 — 가르려면 메시지를 열어야 해 버리는 순간의 비용이 커진다). 로그는 10초에 한 줄로 모아 남기므로 정확한 총량은 이 값으로 본다(F7) |
| `runiverse.running.state.access` | Timer | 러닝 상태 어댑터 → `RunningStateMeter` | 사용 중 — `store=redis\|db`, `target=track\|distance\|status\|combo_snapshot\|combo_pair\|combo_segment`, `phase=running\|finish`, `operation=read\|write`, `result`. 러닝 상태 저장소 왕복의 횟수·시간. 같은 부하를 `store`만 바꿔 돌려 Redis와 DB를 비교한다. 히스토그램을 켠다(100µs~5s — Redis가 1ms 아래라 기본 하한이면 분포가 첫 칸에 몰린다) |
| `runiverse.running.state.loaded` | DistributionSummary (baseUnit `items`) | 러닝 상태 어댑터(종료 집계 읽기) → `RunningStateMeter` | 사용 중 — `store=redis\|db`, `target=track\|combo_segment`. 종료 때 러닝 하나치를 통째로 읽은 개수(좌표 수, 콤보 구간 수). 저장소 물리 단위(stream 엔트리·행)가 아니라 논리 단위로 센다. `state.access`의 시간과 나눠 1,000개당 시간으로 저장소를 비교한다. 히스토그램을 켠다(1~30,000) |
| `runiverse.websocket.connections` | Gauge | `RunningWebSocketHandler`(연결 수립·종료) · `RunningSessionRegistryAdapter`(명부 크기) | 예정 — `domain=running`, `stage=open\|started`. 인스턴스마다 지금 열린 러닝 소켓 수(`open`)와 `RUNNING_START`를 마친 세션 수(`started`). 배포 때 끊기는 인원, 계획 종료가 끝났는지, 자동 확장 신호로 본다. 이름은 정하지 않음 — `…sessions.active` 꼴과 함께 [msa/running-session-server/verification.md](msa/running-session-server/verification.md) 「정하지 않은 것」 1에서 정한다 |
| `runiverse.sse.connections` | Gauge | `AppStreamRegistryAdapter` · `MatchStreamRegistryAdapter` | 예정 — `domain=app\|match`. 인스턴스마다 지금 열린 SSE 연결 수 |
| `runiverse.websocket.closes` | Counter | `RunningWebSocketHandler.afterConnectionClosed` | 예정 — `domain=running`, `code=1000\|1001\|1006\|1009\|1011\|4001\|4500\|unknown`(목록 밖 코드는 `unknown`). 닫힌 러닝 연결 수 — 배포·장애 때 끊김을 close code별로 본다 |
| `runiverse.running.start` | Counter | `StartRunningHandler` → `RunningMetricAdapter` | 예정 — `decision=first\|reconnect`. 처리한 `RUNNING_START` 중 참가자가 `JOINED`였으면 `first`, 이미 `RUNNING`이면 `reconnect`(재연결·앱 재시작). 배포·장애 뒤 재연결 몰림을 본다 |
| `runiverse.running.location.gaps` | Counter | `UpdateRunningLocationHandler` → `RunningMetricAdapter` | 예정 — 배치의 첫 새 좌표 `sequence`가 서버가 받은 마지막 `sequence` + 1보다 클 때 1. 러닝 중 서버 트랙에 빈 순번이 생긴 횟수(재시작·장애로 잃은 좌표, 재전송 누락). 종료 때 칸 단위로 세는 `runiverse.running.finish.gaps`와 다르다 |
| `runiverse.running.finish.step` | Timer | `RunningFinisher` → `RunningMetricAdapter` | 예정 — `step=lock_player\|lock_room\|load_weight\|load_track\|filter\|analyze\|upload_track\|load_weather\|insert_record\|update_avg_pace`, `trigger=user\|goal\|force\|account_deletion`, `result`. 종료 확정 한 건 안의 단계별 시간 — 잠금·커넥션을 쥔 채 도는 단계(S3 업로드·날씨 조회)를 가린다. 히스토그램을 켠다 |
| `runiverse.running.combo.breaks` | Counter | `UpdateRunningComboJudge` → `RunningMetricAdapter` | 예정 — `decision=finished\|paused\|standing\|apart`. 판정이 끊은 콤보 수와 갈래(`RunningComboEvaluator` 판정 순서). 연결이 끊긴 동안은 콤보를 붙잡아 두므로, 재연결 뒤 `apart`가 느는지로 끊김이 콤보를 깼는지 본다 |
| `runiverse.running.replica.lag` | Timer | 메인의 러닝 결과 반영(inbox) → 기록 포트 | 예정 — `event`(결과 이벤트 종류, 이름은 계약을 정할 때), `result`. 러닝 DB 커밋 시각(이벤트 발생 시각)부터 메인 사본 반영 커밋까지 — 종료 뒤 최종 일관 창의 길이다. 히스토그램을 켠다. 복제 슬롯 지연은 인프라 지표다([msa/platform.md](msa/platform.md) 「관측」). `replica`는 기능 폴더가 아직 없다 — 기능 폴더 이름은 반영 inbox 핸들러를 만들 때 맞춘다 |
| `runiverse.running.replica.missing` | Gauge | 대조 작업 → 기록 포트 | 예정 — 마지막 대조에서 러닝 DB에는 있고 메인 사본에는 없는 기록 수(유예 시간이 지난 것만). 0이어야 한다 — 0이 아니면 알람([msa/running-session-server/consistency.md](msa/running-session-server/consistency.md) 「대조」). `replica`는 기능 폴더가 아직 없다 — 기능 폴더 이름은 대조 작업을 만들 때 맞춘다 |
| `runiverse.kafka.messages` | Counter | Kafka 리스너(받는 쪽) | 예정 — `domain`, `event`(토픽 이름에서 `{env}.`를 뺀 것), `result`, `reason`. 받은 메시지 수 — 건너뛰거나 따로 모은 메시지는 `failure`다. 건너뜀 알람을 여기에 건다([msa/decisions.md](msa/decisions.md) D-51) |

- 위 예정 행 중 `websocket.connections`부터 `kafka.messages`까지는 러닝 세션 분리 검증이 쓴다 — 어디에 쓰는지는 [msa/running-session-server/verification.md](msa/running-session-server/verification.md) 「지표」. 세션 서버가 기록하게 돼도 이름·태그는 바꾸지 않는다 — 서버는 공통 태그 `application`으로 가른다.

## 노출

- `management.endpoints.web.exposure.include=health,prometheus` — 나머지 actuator 엔드포인트는 열지 않는다.
- `/actuator/prometheus`는 인증 없이 긁히지만 외부에서 닿으면 안 된다 — 네트워크에서 막는다(로드밸런서에 노출하지 않거나 별도 관리 포트).
- 응답 시간 백분위(p95·p99)는 Grafana에서 계산하도록 히스토그램을 켠다. 앱에서 백분위를 직접 계산하지 않는다 — 인스턴스 여럿의 값을 합칠 수 없다.
