# AI 전용 서버 — 왜 따로인가

음악 생성을 메인 서버(running-service) 안에 두지 않고 따로 둔 이유를 결론으로 적는다. 근거는 셋이고 모두 지금 사실이다 — 언어·런타임이 다르고, 생성이 길고 비동기이며, 주인과 저장소가 다르다.

> 상태: 운영 중 — 이미 따로 돈다. 근거(주인·런타임)가 바뀔 때만 [decisions.md](../decisions.md) 항목과 함께 고친다.

| 무엇 | 어디 |
|---|---|
| 그래서 정한 것 | 「결정」 |
| 경계·흐름·실행 환경 | [architecture.md](architecture.md) |
| 러닝 세션 서버를 나누는 이유 | [running-session-server/rationale.md](../running-session-server/rationale.md) |

## 언어 · 런타임

- **Python과 GPU 모델이다** — Qwen3-VL → ACE-Step 1.5로 곡을 만들고 추론은 SageMaker에서 돈다([architecture.md](architecture.md) 「실행 환경」).
- **재료와 결과** — 요청 이벤트의 `running_summary` · 회고 글 · 사진을 재료로 기본곡과 사진마다 곡을 만든다. 계약은 [api-spec.md](../../api-spec.md) 9-5.
- **결론** — 메인(Java·Spring Boot) 프로세스에 넣을 수 없는 스택이라 다른 배포물이다. GPU 자원도 메인 인스턴스와 따로 정한다.

## 긴 비동기 생성

- **요청 하나가 여러 곡을 만든다** — 기본곡 1곡 + 사진마다 1곡(사진은 많아야 5장 — `RecordCard.MAX_IMAGES`).
- **추론 호출이 비동기다** — 완료 이벤트의 `invoke.http_status`가 202이고, 실패 단계는 `INVOKE`·`INFERENCE`로 갈린다. AI는 재시도를 안에서 끝내고 요청마다 최종 결과를 한 번 보낸다([api-spec.md](../../api-spec.md) 9-5).
- **결론** — 카드 저장 응답이 기다릴 수 있는 길이가 아니고, AI가 멈춘 동안에도 카드 저장은 돌아야 한다. 그래서 요청·완료 두 토픽으로 주고받고([decisions.md](../decisions.md) D-03), 요청은 카드와 한 트랜잭션에 아웃박스로 쓴다(D-06). 메인은 결과를 기다리지 않고 음악을 `GENERATING`으로 보여 준다.
  - AI가 멈춰도 요청은 Kafka에 7일 남는다([kafka.md](../kafka.md) 「토픽」).

## 다른 주인 · 저장소

- **주인이 AI 쪽이다** — 저장소는 `runiverse-ai`이고 Kafka 계정 이름도 같다. 메인은 AI 저장소·DB를 모르고, 둘이 함께 보는 것은 api-spec 9-5 계약과 [kafka.md](../kafka.md)의 이름·권한뿐이다.
- **배포와 확인이 AI 쪽 주기를 따른다** — 이 폴더와 outbox-inbox.md의 'AI 쪽 확인' 표시가 그 자리다.
- **결론** — AI 쪽 배포·장애가 메인을 막지 않아야 한다. 계약을 바꿀 때는 api-spec 9-5를 먼저 고치고 AI 쪽 확인을 받은 뒤 코드를 바꾼다.

## 러닝 세션과 다른 점

AI 서버에서 맞는 근거 둘(주인, 런타임)이 러닝 세션 서버에서는 버린 근거다([running-session-server/rationale.md](../running-session-server/rationale.md) 「버린 근거 X1–X5」 X2·X3). 같은 '따로 둔다'라도 이유가 다르다.

| 근거 | AI 전용 서버 | 러닝 세션 서버 |
|---|---|---|
| 주인 · 팀 | 다르다 — AI 쪽이 `runiverse-ai`를 맡는다 | 같다 — 같은 백엔드 개발자가 양쪽을 고친다 (X2 버림) |
| 언어 · 런타임 | 다르다 — Python + GPU 모델 | 같다 — Java·Spring Boot. 연결 위주 맞춤은 설정으로 된다 (X3 버림) |
| 처리 시간 | 길고 비동기 — 결과 한 번 | 실시간 — 러닝 중 계속 주고받는다 |
| 나누는 이유 | 위 셋 | 안정성 — 메인 배포·장애가 진행 중인 러닝을 끊지 않게 ([running-session-server/rationale.md](../running-session-server/rationale.md) 「분리 이유 — 운영에서 확인한 것」, D-45) |

## 결정

| D | 한 줄 | 상태 |
|---|---|---|
| [D-03](../decisions.md) | 메인과 AI는 Kafka 양방향으로만 — 메인은 AI 서버도 SageMaker도 부르지 않는다 | 정함 |
| [D-06](../decisions.md) | 이벤트는 아웃박스 + CDC로 보낸다 — 앱에는 Kafka 프로듀서가 없다 | 정함 |
| [D-05](../decisions.md) | 기록 카드 PR을 셋으로 — AI 서버는 받는 쪽 inbox를 따로 둔다 | 정함 |
| [D-07](../decisions.md) | 카드·사진 ID는 UUIDv7, 음악 key는 AI가 정한다 | 정함 (D-02를 바꿈) |

분리 전 다른 결정은 [decisions.md](../decisions.md) 「분리 전 결정 (10-15 ~ 11-23)」.
