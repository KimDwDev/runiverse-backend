# Metrics Convention

메트릭 이름·태그·계측 종류·기록 위치 규칙 — 메트릭을 새로 만들거나 고칠 때 모두 이 규칙을 따른다.

## 수집 구조

- 앱은 Micrometer로 기록하고 `/actuator/prometheus`로 노출한다. Prometheus가 주기적으로 긁어 가고(pull), Grafana가 Prometheus를 조회한다.
- 앱은 누적값만 들고 있다. 시각별 기록은 Prometheus에 쌓이고, 증가 속도(`rate()`)는 Grafana에서 계산한다.
- 모든 메트릭에 공통 태그 `application=running-service`를 붙인다.

## 로그와 메트릭의 역할

| | 로그 | 메트릭 |
|---|---|---|
| 답하는 질문 | 누가, 왜 실패했나 | 얼마나 많이, 얼마나 빨리 |
| 식별자 | `userId`·`roomId`로 한 건을 찾는다 | 식별자를 쓰지 않는다 — 합계와 분포만 본다 |

- 알림은 메트릭으로 울리고, 원인은 로그로 찾는다. 알림 → 같은 시간대의 ERROR 로그 → requestId 순서로 추적한다.

## 이름

```
runiverse.<도메인>.<기능 폴더>.<동작>
```

- `<도메인>`은 `application/` 아래 패키지(`auth`·`user`·`match`·`running`·`scheduling`), `<기능 폴더>`는 그 아래 `command/`·`query/`의 기능 패키지다.
- `<동작>`은 핸들러 이름의 동사를 소문자로 쓴다(`OpenMatchStreamHandler` → `open`). 동사가 기능 폴더 이름과 같으면 생략한다(`signup/SignUpHandler` → `runiverse.auth.signup`).
- 클래스 이름을 그대로 쓰지 않는다 — 클래스 이름이 바뀌면 새 시계열이 생겨 이전 기록·대시보드·알림이 끊긴다. 하는 일이 같으면 클래스 이름이 바뀌어도 메트릭 이름은 유지한다.
- 소문자와 점(`.`)으로 쓴다. Prometheus로 나갈 때 `_`로 바뀌고 종류별 접미사가 붙는다(`runiverse_auth_signup_total`).
- 단위를 이름에 넣지 않는다 — Timer는 초, 나머지는 `baseUnit`으로 지정한다.
- 결과를 이름으로 가르지 않는다(`...signup.success` ✗) — 태그 `result`로 가른다.

| 핸들러 | 메트릭 이름 |
|---|---|
| `match/stream/OpenMatchStreamHandler` | `runiverse.match.stream.open` |
| `match/stream/CloseMatchStreamHandler` | `runiverse.match.stream.close` |
| `auth/emailverification/SendEmailVerificationHandler` | `runiverse.auth.emailverification.send` |
| `auth/signup/SignUpHandler` | `runiverse.auth.signup` |

## 태그

**값의 종류가 정해진 것만 태그로 쓴다.** 태그 값 조합마다 시계열이 하나씩 생긴다 — 사용자 수만큼 늘어나는 값을 넣으면 Prometheus 메모리가 버티지 못한다.

| 쓴다 | 쓰지 않는다 |
|---|---|
| `result=success\|failure` | `userId`, `roomId`, `requestId`, `scheduledJobId` |
| `reason=<ErrorCode 이름>` | 이메일, 좌표, 예외 메시지 |
| `provider=kakao\|google\|unknown` | 요청 경로 원문 — 템플릿(`/users/{userId}/profile`)만 |

- 태그 key는 camelCase, 값은 소문자로 쓴다. `reason`만 `ErrorCode` 이름을 그대로 쓴다.
- **같은 이름의 메트릭은 태그 key 구성이 항상 같아야 한다** — Prometheus 레지스트리가 강제한다. 성공에도 `reason`을 붙이고 값은 `none`으로 쓴다.
- 사용자 입력을 태그 값으로 그대로 쓰지 않는다 — 지원하지 않는 provider처럼 목록 밖의 값은 `unknown`으로 바꾼다.
- 원인을 `ErrorCode`보다 잘게 나누지 않는다 — 더 자세한 원인은 로그에 있다.

## 계측 종류

| 종류 | 쓰는 곳 | 예 |
|---|---|---|
| Counter | 일어난 횟수 | 로그인 시도, 외부 API 실패 |
| Timer | 걸린 시간 + 횟수 | 외부 API 호출, 러닝 종료 처리 |
| Gauge | 지금 이 순간의 값 | WebSocket·SSE 연결 수 |
| DistributionSummary | 시간이 아닌 값의 분포 | 러닝 거리 |

- 횟수와 시간을 함께 보려면 Counter를 따로 두지 않고 Timer 하나로 쓴다 — Timer가 횟수도 센다.

## 기록 위치

- application은 Micrometer를 import하지 않는다. 업무 결과 메트릭은 `port/out`의 기록 포트를 호출하고, infrastructure가 `MeterRegistry`로 구현한다.
- 외부 시스템 호출 시간·실패는 infrastructure 어댑터가 직접 기록한다.
- 연결 수 같은 presentation의 상태는 presentation이 기록한다.

## 기본 제공과 직접 만드는 것

설정만으로 나오는 것은 직접 만들지 않는다.

| 기본 제공 | 내용 |
|---|---|
| `http.server.requests` | 엔드포인트별 응답 시간·상태 코드 |
| `jvm.*`, `process.*` | 메모리·GC·스레드·CPU |
| `hikaricp.*` | DB 커넥션 풀 |
| `lettuce.*` | Redis 명령 |
| `logback.events` | 레벨별 로그 수 — ERROR 증가 알림에 쓴다 |

직접 만드는 것은 이 표에 먼저 추가하고 나서 만든다.

| 이름 | 종류 | 태그 | 기록 위치 | 설명 |
|---|---|---|---|---|
| `runiverse.auth.signup` | Counter | `result`, `reason` | `SignUpHandler` | 이메일 회원가입 시도. 소셜 첫 로그인은 세지 않는다 |
| `runiverse.auth.login` | Counter | `result`, `reason` | `LoginHandler` | 이메일 로그인 시도 |
| `runiverse.auth.oauthlogin` | Counter | `provider`, `result`, `reason` | `OauthLoginHandler` | 소셜 로그인 시도 |

`reason` 값(성공은 모두 `none`):
- `runiverse.auth.signup`: `EMAIL_NOT_VERIFIED`, `EMAIL_ALREADY_EXISTS`
- `runiverse.auth.login`: `INVALID_CREDENTIALS`
- `runiverse.auth.oauthlogin`: `UNSUPPORTED_PROVIDER`, `OAUTH_CODE_EXCHANGE_FAILED`, `OAUTH_EMAIL_NOT_PROVIDED`, `EMAIL_ALREADY_EXISTS`

## 노출

- `management.endpoints.web.exposure.include=health,prometheus` — 나머지 actuator 엔드포인트는 열지 않는다.
- `/actuator/prometheus`는 인증 없이 긁히지만 외부에서 닿으면 안 된다 — 네트워크에서 막는다(로드밸런서에 노출하지 않거나 별도 관리 포트).
- 응답 시간 백분위(p95·p99)는 Grafana에서 계산하도록 히스토그램을 켠다. 앱에서 백분위를 직접 계산하지 않는다 — 인스턴스 여럿의 값을 합칠 수 없다.
