# 서비스 사이 구조 (MSA)

Runiverse는 메인 서버(running-service)·러닝 세션 서버·AI 전용 서버 셋으로 나뉘고, Kafka·DB·Redis·S3 같은 플랫폼을 함께 딛는다. 이 문서는 지도다 — 실제 이름, 지금 도는 구성과 목표 구성, 서비스 사이 통신 규칙, 이 폴더를 쓰고 고치는 규칙을 둔다. 서비스 사이 이벤트를 만들거나, Kafka·CDC를 건드리거나, 서비스를 나누는 일을 할 때 여기서 시작한다.

> 상태: 「구성 — 지금」 「환경」 「지금 운영 중인 것」은 test·prod에서 확인한 것만 적는다. 「구성 — 목표」는 계획이다 — 요소마다 `정함 (D-xx)` · `제안` · `정하지 않음`.

| 무엇 | 어디 |
|---|---|
| 러닝 세션 서버 — 왜 나누는가 · 구조 · 일관성 · 검증 · 옮기기 · 진행 단계 | [running-session-server/README.md](running-session-server/README.md) |
| AI 전용 서버 — 지금 상태 · 왜 따로인가 · 구조 | [ai-server/README.md](ai-server/README.md) |
| 무엇을 정했고 무엇이 바뀌었나 | [decisions.md](decisions.md) |
| 데이터 · 인프라 배치 — RDS · Redis · 러닝 DB CDC · S3 · 관측 · 새 이름 / EKS 앱 워크로드 | [platform.md](platform.md) / [eks.md](eks.md) |
| 이벤트를 보내고 받는 코드 규칙 | [outbox-inbox.md](outbox-inbox.md) |
| Kafka 클러스터 — 이름 · 토픽 · 보안 · 접속 | [kafka.md](kafka.md) |
| 아웃박스 → Kafka 발행 — Debezium · 복제 슬롯 / 커넥터 설정 | [cdc.md](cdc.md) / `infra/kafka/connectors/` — `test-outbox.json` · `prod-outbox.json` (비밀번호·prod DB 주소는 서버 파일 참조만) |
| 이벤트 계약 — 메인 ↔ AI | [api-spec.md](../api-spec.md) 9-5 (메인 ↔ 러닝 세션 계약은 정하면 14장) |
| 스키마 / 코드 레이어 | [erd.md](../erd.md) / 레이어 규칙 ([docs/architecture.md](../architecture.md)) |
| 분리 검증 — k6 하네스 · 사전 등록 · 실행 순서 · 결과 | [load_test/msa/README.md](../../load_test/msa/README.md) |

> 주소·비밀번호·키·토큰은 이 폴더에 적지 않는다 — Parameter Store 이름까지만. 운영 증거는 합계로만 적는다.

## 서비스 이름

| 보이는 이름 | 문서 폴더 | 저장소 · 코드 | Kafka 계정 | 상태 |
|---|---|---|---|---|
| 메인 서버 | 없음 — `docs/*`가 메인 문서 | 이 저장소의 `running-service` (Gradle 프로젝트도 같은 이름) | `{env}.running-service` | 운영 중 |
| 러닝 세션 서버 | [`running-session-server/`](running-session-server/README.md) | 모놀리스 안: Gradle 서브프로젝트 `running-session`, 패키지 `com.runiverse.running_session` — 제안 (D-35), 별도 프로세스 호스트 `running-session-app` — 제안 (D-61). 나눈 뒤: 저장소 · 이미지 `running-session-server` — 제안 (D-61) | `{env}.running-session-server` — 제안 (D-61) | 계획 — [running-session-server/README.md](running-session-server/README.md) 「진행 단계」 |
| AI 전용 서버 | [`ai-server/`](ai-server/README.md) | 저장소 `runiverse-ai` (AI 쪽) | `{env}.runiverse-ai` | 운영 중 — [ai-server/README.md](ai-server/README.md) 「지금 상태」 |

- **실제 이름은 이 표가 정본이다** — 폴더 이름은 보이는 이름을 따른다. AI는 폴더(`ai-server/`)와 실제 이름(`runiverse-ai`)이 다르다.
- **'메인 서버'는 모놀리스 running-service다** — 러닝 세션을 떼어 낸 뒤에도 같은 이름이다. 세션 쪽 이름에는 `running-service`와 헷갈리지 않게 `session`을 넣는다.

## 구성 — 지금

> 운영 중 — 11-24 기준. 러닝 세션은 아직 메인 서버 안에서 돈다.

```text
 Flutter 앱 (워치는 폰을 거친다)
    │ HTTPS · SSE · WebSocket
    ▼
   ALB
    │
 메인 서버 running-service (여러 대) ── 러닝 세션 · 매칭 · 기록 카드가 한 프로세스
    │   └ Redis (EC2 한 대, AOF) ── 러닝 실시간 상태 · 토큰 · 인스턴스 사이 pub/sub
    │            ▲
    │ INSERT     │ 완료 이벤트 구독 + inbox (11-20 test · prod)
    ▼            │
 Postgres        │
 outbox_events   │
    │ WAL        │
    ▼            │
 Debezium ──▶ Kafka ──▶ AI 전용 서버 ──▶ S3 (음악)
 (Connect)     ▲            │
               └────────────┘ 완료 이벤트
```

## 구성 — 목표

> 계획 — 목표 구성의 그림은 이것 하나다. 다른 문서는 여기로 링크한다.

```text
 Flutter 앱 (워치는 폰을 거친다)
    │ HTTPS · SSE · WebSocket
    ▼
   ALB ── 경로 규칙
    │
    ├─────────────────────────▶ 메인 서버 (running-service) ── REST · SSE
    │                             모집 · 회원 · 관계 · 알림 · 채팅 · 기록 카드 · 7-1
    │                             ├ 메인 Redis (EC2, AOF)
    │                             ├ 서비스 DB (RDS) ── 메인 사본 테이블 · 아웃박스 · inbox
    │                             └ OAuth (카카오 · 구글 · 애플)
    │                                    ▲
    │                                    │ 동기 호출 ── 허용 목록만
    │                                    ▼
    └─────────────────────────▶ 러닝 세션 서버 ── WebSocket · 결과 조회 (6-1 · 6-2)
                                  명단 확정 뒤 러닝 · 끝내기 · 강제 종료
                                  ├ 세션 Redis (EC2, AOF)
                                  ├ 러닝 DB (RDS) ── 러닝 기록 정본 · 아웃박스 · inbox
                                  └ S3 원본 트랙

 서비스 DB 아웃박스 ── WAL ──▶ Debezium 커넥터 (1) ──┐
 러닝 DB 아웃박스   ── WAL ──▶ Debezium 커넥터 (2) ──┼──▶ Kafka (EC2 한 대) ──▶ 받는 서비스의 inbox
 AI 전용 서버 (완료 발행 방식은 정하지 않음) ────────┘
   └ AI DB · SageMaker · S3 음악

 Kafka 위 이벤트  세션 → 메인  참가자 결과 — 정함 (D-72)
                  메인 → 세션  방 확정 · 사용자 변경 — 정함 (D-71, D-72)
                  메인 ↔ AI    음악 요청 · 완료 — 정함 (D-03)
```

| 요소 | 상태 | 자세히 |
|---|---|---|
| 러닝 세션 서버를 별도 저장소의 별도 서버로 | 정함 (D-47) — 옮기는 때(운영 추출 앞)도 정함 (D-62) | [running-session-server/migration.md](running-session-server/migration.md) 「단계 M0–M9」 |
| 경계 — 매칭 방은 메인, 러닝 방(모집 마감 뒤 대기 · 이탈 · 달리기)은 세션 | 정함 (D-26, D-71, D-73) | [running-session-server/architecture.md](running-session-server/architecture.md) 「경계 — 생애 단계」 |
| 메인 ↔ 세션 이벤트 — 방 확정 · 사용자 변경 · 참가자 결과 | 정함 (D-71, D-72) — 이름 · 본문은 api-spec 14장을 쓸 때 | [running-session-server/architecture.md](running-session-server/architecture.md) 「넘기기」 「이벤트 (후보)」 |
| 러닝 DB가 러닝 기록의 정본, 메인은 사본 테이블 (커넥터 (2) → 메인 inbox) | 정함 (D-24) — 사본은 지금 `running_records` · `running_segments` 그대로 (D-68) | [running-session-server/architecture.md](running-session-server/architecture.md) 「메인 사본과 반영」 |
| 결과 이벤트를 받기 전에 처리 못 한 메시지 모으기 · 건너뜀 알람 · 대조 | 정함 (D-51) — DLT 토픽인지 보관 테이블인지는 정하지 않음 | [running-session-server/consistency.md](running-session-server/consistency.md) 「대조」 |
| ALB 경로 규칙 — 러닝 WebSocket · 결과 조회 6-1 · 6-2는 세션, 나머지는 메인. 7-1 · 기록 카드는 메인 반영 뒤 | 정함 (D-26, D-58) — 새 규칙의 순위 값은 제안 | [running-session-server/architecture.md](running-session-server/architecture.md) 「공개 경로」 「결과 조회」 |
| 동기 호출 — 허용 목록만 | 정함 (D-52) — 목록은 제안 (D-53) | [running-session-server/architecture.md](running-session-server/architecture.md) 「동기 호출 허용 목록」 |
| 서비스 사이 saga — 러닝 중 계정 탈퇴 하나 | 정함 (D-43, D-44, D-70) — 메인 오케스트레이션 | [running-session-server/consistency.md](running-session-server/consistency.md) 「계정 탈퇴 saga」 |
| 플랫폼 — RDS 둘 · Redis 서비스마다 EC2 · Kafka EC2 한 대 · 추적 OTel 에이전트 | 정함 (D-48 · D-49 · D-50 · D-55) — 브로커 셋 · Strimzi · MSK는 정하지 않음 | [platform.md](platform.md) |
| 앱 워크로드는 EKS | 정함 (D-23, D-48) — 세부는 제안 (D-41) | [eks.md](eks.md) |
| AI 완료 발행 (AI 아웃박스 + CDC) · AI DB 자리 | 정하지 않음 | [ai-server/architecture.md](ai-server/architecture.md) 「완료 발행 — 지금과 그림」, [platform.md](platform.md) 「정하지 않은 것」 |

## 왜 나누는가

- **러닝 세션 서버** — 메인 배포·장애가 진행 중 러닝을 끊지 않게 한다. 앱이라 배포를 밤까지 미룰 수 없고, 매칭은 끊겨도 견디지만 러닝은 콤보 때문에 중간에 끊기면 크게 꼬인다 — 정함 (D-45). 근거는 [running-session-server/rationale.md](running-session-server/rationale.md), 근거마다 k6로 깨질 수 있게 시험하고(D-25) 판정은 [running-session-server/verification.md](running-session-server/verification.md) 「판정과 결과」에 둔다.
- **AI 전용 서버** — 언어·런타임(Python, GPU 추론)이 다르고, 요청 하나가 여러 곡을 오래 만들며, 주인과 저장소가 다르다 — 정함 (D-03). 근거는 [ai-server/rationale.md](ai-server/rationale.md).
- **메인 서버는 하나로 둔다** — 콘텐츠와 러닝 밖 기능을 계속 고치고 자주 배포하는 쪽이고, 매칭은 배포 때 끊겨도 견딘다(D-45). 더 나누면 DB·커넥터·파이프라인 운영이 작은 팀에 하나씩 더 붙는다([running-session-server/rationale.md](running-session-server/rationale.md) 「반대 근거 AG1–AG6」 AG4).

## 누가 무엇을 맡는가

| 서비스 | 맡는 것 (지금) | 갖는 데이터 (지금) |
|---|---|---|
| 메인 서버 (running-service) | 앱 API 전부 — 러닝 세션(WebSocket)도 여기서 돈다. 기록 카드 저장, 음악 생성 상태 | Postgres — 사용자·매칭·러닝 기록·기록 카드의 정본. Redis(EC2 한 대) — 러닝 실시간 상태·토큰·pub/sub |
| 러닝 세션 서버 | 없음 — 계획 | 없음 |
| AI 전용 서버 | 음악 생성 (모델·추론 인프라 포함) | 자기 저장소(inbox 포함) — [ai-server/architecture.md](ai-server/architecture.md) 「경계」 |
| Debezium (Kafka Connect) | 커밋된 아웃박스 행을 Kafka로 발행 | 없음 — 어디까지 보냈는지는 복제 슬롯과 Connect 오프셋 |
| Kafka | 서비스 사이 이벤트 전달 | 이벤트 7일 보관 |

- **목표** — 테이블·S3 객체·예약 작업 종류·스냅샷의 주인은 [running-session-server/architecture.md](running-session-server/architecture.md) 「데이터 주인」, Redis 키·채널의 주인은 [platform.md](platform.md) 「Redis」.
- **데이터는 서비스마다 따로 갖는다** — 다른 서비스의 DB·Redis를 읽거나 쓰지 않는다. 필요한 값은 이벤트에 실어 보낸다(예: 음악 요청의 `running_summary` — [ai-server/architecture.md](ai-server/architecture.md) 「경계」).
- **서비스끼리 함께 쓰는 저장소는 S3뿐이다** — 버킷마다 쓰는 쪽·읽는 쪽은 [platform.md](platform.md) 「S3」, AI와 주고받는 key는 [ai-server/architecture.md](ai-server/architecture.md) 「함께 쓰는 것」.

## 통신 — 어디로 무엇이 지나가는가

| 사이 | 수단 | 상태 | 규칙 |
|---|---|---|---|
| 앱 ↔ 메인 서버 | HTTPS · SSE · WebSocket | 운영 중 | [api-spec.md](../api-spec.md) · [api-convention.md](../api-convention.md) |
| 메인 인스턴스 ↔ 인스턴스 | Redis pub/sub | 운영 중 | 레이어 규칙 ([docs/architecture.md](../architecture.md)) 「다중 인스턴스 (로드 밸런서 뒤)」 |
| 메인 ↔ AI 전용 서버 | **Kafka만** | 운영 중 (D-03) | 이 절, [ai-server/architecture.md](ai-server/architecture.md) 「계약」 |
| 앱 ↔ 러닝 세션 서버 | WebSocket · 결과 조회 HTTPS (ALB 경로 규칙) | 정함 (D-26, D-58) — 순위 값은 제안 | [running-session-server/architecture.md](running-session-server/architecture.md) 「공개 경로」 |
| 메인 ↔ 러닝 세션 서버 | Kafka + 허용 목록의 동기 호출 | 정함 (D-24, D-52) — 이벤트 · 호출 목록은 제안 (D-28, D-53) | [running-session-server/architecture.md](running-session-server/architecture.md) 「이벤트 (후보)」 「동기 호출 허용 목록」 |
| 세션 인스턴스 ↔ 인스턴스 | 세션 Redis pub/sub | 정함 (D-49) | [running-session-server/runtime.md](running-session-server/runtime.md) |

- **서비스 사이는 Kafka로 주고받는다** — 한쪽이 멈춰도 다른 쪽은 이벤트를 남기고 계속 돈다. 메인은 AI 서버를 HTTP로 부르지 않는다.
  - **예외 하나 — 허용 목록에 적힌 동기 호출** — 정함 (D-52). 목록과 호출마다의 이유·시간 제한·멱등 키·실패 때 동작은 [running-session-server/architecture.md](running-session-server/architecture.md) 「동기 호출 허용 목록」(후보는 제안, D-53). 목록 밖 호출은 금지이고 `check_conventions.py`로 막는다(D-52 — 검사 규칙은 [running-session-server/migration.md](running-session-server/migration.md) 「경계 검사」 6). 지금 허용된 호출은 없다.
- **서비스 사이에 Redis pub/sub을 쓰지 않는다** — 받는 쪽이 그 순간 없으면 사라진다. Kafka는 이벤트가 남아 돌아와서 이어 읽는다. Redis는 서비스마다 따로 두고(D-49), pub/sub은 한 서비스의 인스턴스 사이에서만 쓴다.
- **받는 서비스는 용도마다 컨슈머 그룹 하나로 받는다** — 그룹은 `{env}.<서비스>.<용도>`다([kafka.md](kafka.md) 「이름 — 환경 접두어 `{env}`」). 이벤트 하나는 그 서비스의 한 인스턴스만 처리하고, 결과는 DB에만 쓴다. 앱 연결을 든 인스턴스에 알려야 하면 커밋한 뒤 그 서비스 안의 Redis pub/sub으로 넘긴다 — 기존 SSE 푸시와 같은 방식이다.
  - 지금 토픽은 파티션이 1이라 그룹마다 한 대만 읽는다([kafka.md](kafka.md) 「지금 설정 때문에 지켜야 할 것」). 새 토픽의 파티션 수는 [platform.md](platform.md) 「새 이름」에서 정한다.
- **보내는 쪽은 잃지 않는 것을, 받는 쪽은 두 번 처리하지 않는 것을 맡는다** — 전달은 최소 한 번이다. 메인·세션은 아웃박스로 보내고(CDC가 발행) inbox로 받는다(D-06, [outbox-inbox.md](outbox-inbox.md)). AI 전용 서버의 발행 방식은 [ai-server/architecture.md](ai-server/architecture.md) 「완료 발행 — 지금과 그림」.
  - **처리하지 못한 메시지를 따로 모은다** — 정함 (D-51), 러닝 결과 이벤트를 받기 전(M2)에 만든다. DLT 토픽으로 정하면 앱이 Kafka에 직접 쓰는 하나뿐인 예외가 되고, 보관 테이블이면 예외가 없다 — 어느 쪽인지는 정하지 않음([outbox-inbox.md](outbox-inbox.md) 「받는 쪽 — Inbox」 「정하지 않은 것」).

## 이벤트 색인

> 운영 중인 토픽만 적는다. 계약은 [api-spec.md](../api-spec.md) 9-5가 정본이다.

| 토픽 (`{env}.` 생략) | 방향 | 보내는 방식 | 상태 |
|---|---|---|---|
| `record-card.music.requested` | 메인 → AI | 아웃박스 + CDC (커넥터 `test-outbox` · `prod-outbox`) | test·prod CDC 동작 중 |
| `record-card.music.generated` | AI → 메인 | AI 전용 서버가 직접 발행 | test·prod 구독 중 (11-20) — AI 서버의 헤더 `id` 반영 전 |

- **쓰는 코드와 받는 쪽 중복 거르기** — [outbox-inbox.md](outbox-inbox.md) 「지금 있는 이벤트」. 파티션·보관은 [kafka.md](kafka.md) 「토픽」. 기록 카드 음악의 흐름은 [ai-server/architecture.md](ai-server/architecture.md) 「흐름 — 기록 카드 음악」.
- **러닝 세션 이벤트는 아직 없다** — 후보는 [running-session-server/architecture.md](running-session-server/architecture.md) 「이벤트 (후보)」, 이름은 [platform.md](platform.md) 「새 이름」. 정하면 api-spec 14장에 계약을 적고, 만들어 확인한 뒤 이 표에 더한다.

## 환경

> 운영 중 — 계획 중인 배치(러닝 DB · 둘째 커넥터 · Redis 둘)는 [platform.md](platform.md) 「배치 — 지금과 목표」에 있고, 만들어 확인한 뒤 이 표에 더한다.

| 환경 | DB | Kafka | CDC |
|---|---|---|---|
| `dev` | 로컬 Postgres | 개발 클러스터 (`dev.*`) | 없음 |
| `test` | EC2에 직접 설치한 Postgres (Kafka와 같은 EC2) | 개발 클러스터 (`test.*`) | 커넥터 `test-outbox` |
| `prod` | RDS | 운영 클러스터 | 커넥터 `prod-outbox` |

- dev·test는 Kafka 클러스터 하나를 이름 접두어로 나눠 쓴다([kafka.md](kafka.md) 「이름 — 환경 접두어 `{env}`」).
- **`KAFKA_ENV`는 붙는 DB를 따른다** — 로컬 앱이라도 test DB에 붙으면 `test`다([cdc.md](cdc.md)).

## 지금 운영 중인 것

| 무엇 | 상태 |
|---|---|
| 아웃박스 쓰기 (9-2) | 됨 — 쓰는 곳(9-3 수정·9-6 다시 만들기 포함)은 [outbox-inbox.md](outbox-inbox.md) 「지금 있는 이벤트」 |
| Kafka 클러스터 (개발·운영) | 됨 — 계정·토픽·ACL |
| CDC (test·prod) | 됨 — 커넥터 동작, 발행·재기동·heartbeat 검증, prod 슬롯 지연 경보 |
| 아웃박스 코드 배포 | 됨 — test 서버·prod 모두 배포된 앱의 카드가 Kafka까지 나감 |
| 완료 구독 + inbox (메인) | 됨 — 11-20 test·prod 배포(#134, prod 1.3.1). 확인 내용은 [outbox-inbox.md](outbox-inbox.md) 「받는 쪽 — Inbox」 「검증 — 테스트가 못 보는 것」 |
| AI 전용 서버 연동 | [ai-server/README.md](ai-server/README.md) 「지금 상태」 |
| 러닝 세션 서버 | 없음 — [running-session-server/README.md](running-session-server/README.md) 「진행 단계」 |

## 서비스나 이벤트를 더할 때

- **이벤트** — 계약을 api-spec에 먼저 적고 [outbox-inbox.md](outbox-inbox.md) 「새 이벤트를 더할 때」 체크리스트를 따른다. 이 문서의 「이벤트 색인」에 더한다.
- **서비스** — 계정 `{env}.<서비스>`, 컨슈머 그룹 `{env}.<서비스>.<용도>`, 토픽·그룹 권한(이름 하나씩), 그 환경 클러스터의 CA 인증서([kafka.md](kafka.md)). 만들기 전의 이름은 [platform.md](platform.md) 「새 이름」에 둔다. 이 문서의 「서비스 이름」 「누가 무엇을 맡는가」 「이벤트 색인」에 더한다.
- **DB를 가진 서비스가 아웃박스로 보낼 때** — DB마다 커넥터·슬롯을 하나씩 둔다([cdc.md](cdc.md) 「새 환경에 CDC를 붙이는 순서」, 러닝 DB는 [platform.md](platform.md) 「러닝 DB CDC」).
- **서비스 폴더** — README · rationale · architecture부터 두고, 결정은 [decisions.md](decisions.md)에 먼저 적는다.

## 문서 규칙

> 정함 (D-60).

- **상태 표시** — 계획 문서는 머리말 아래 `> 상태:` 줄을 둔다. 절·행은 `운영 중` / `정함 (D-xx)` / `제안` / `정하지 않음`, 결정 항목은 `정함` / `제안` / `바뀜 → D-yy` / `버림`([decisions.md](decisions.md) 「적는 법」), 단계는 `됨` / `진행 중` / `대기`, 근거는 `가설` 뒤 `지지` / `기각` / `판정 못 함` / `분리 뒤에만` / `안 돌림`(버린 근거 X는 `기각`에서 시작하고, 다시 열면 `가설`). 운영 문서(kafka · cdc · outbox-inbox)의 기존 표시(됨 · 확인 전 · AI 쪽 · ✅ · ⚠️)와 metrics-convention의 예정 · 사용 중은 그대로 쓴다.
- **지금과 목표를 한 문단에 섞지 않는다** — 운영 문서와 이 문서의 「구성 — 지금」 「환경」 「지금 운영 중인 것」에는 test·prod에서 확인한 것만 적는다.
- **사실마다 집이 하나다** — 다른 문서는 상대 경로 + 「절 이름」으로 링크한다. 계약(api-spec)·스키마(erd)·기능 규칙(feature-spec)·Kafka 설정(kafka.md)은 옮겨 적지 않는다 — 집 목록은 아래 「고치는 규칙」.
- **결론만 적는다** — 개념 설명·원리 표·공부 내용·논쟁 글은 넣지 않는다. 문장마다 "지우면 우리 코드·운영 결정이 틀어지나"를 묻는다. 바깥 자료는 결론 옆 링크로만 둔다.
- **숫자에는 출처와 날짜를 단다** — run id · PR #n · 커밋 · 조회 날짜. 날짜는 KST `MM-DD`.
- **설계 문서는 「정하지 않은 것」 번호 목록으로 끝낸다** — 정하면 `~~항목~~ — **정했다 → D-xx**`로 지우고 같은 PR에서 본문을 고친다.
- **알려진 결함은 번호 없이 이름으로 쓴다** — 예: 늦은 DISCONNECTED.
- **길이** — 설계 문서는 짧게 둔다(README 200줄, architecture · migration 400줄 안팎). decisions.md는 항목이 쌓이는 기록이라 예외다.

**번호** — 한 번 쓴 번호는 다시 쓰지 않는다. 버린 항목도 번호를 남긴다. F는 수정, C는 장애, R은 플랫폼 검증, L은 부하 수준이다 — 섞어 쓰지 않는다.

| 번호 | 뜻 | 정의하는 곳 |
|---|---|---|
| `D-nn` · `RS-D-nn` | 결정 · 저장소를 나눈 뒤 러닝 세션 안에서만 뜻이 있는 결정 | [decisions.md](decisions.md) · 러닝 세션 저장소 (나중) |
| G1–G8 · AG1–AG6 · X1–X5 | 분리 근거 · 반대 근거 · 버린 근거 | [running-session-server/rationale.md](running-session-server/rationale.md) |
| SC0–SC14 · C1–C7 · L30 · L60 · L100 | k6 시나리오 (SC1–SC7 핵심, `S`로 쓰지 않는다 — S3와 겹친다) · SC3 안 장애 · REST 부하 수준 | [running-session-server/verification.md](running-session-server/verification.md) 「번호」 |
| A · A′ · B0 · B2 (+ 변형) | 비교군 | [running-session-server/verification.md](running-session-server/verification.md) 「비교군」 |
| R1–R9 | 플랫폼 검증 | [running-session-server/verification.md](running-session-server/verification.md) 「플랫폼 검증 R1–R9」 |
| `K-SCn-m` · `K-Rn-m` | 판정 기준 · 단계 나가는 기준 후보 | [load_test/msa/PREREG.md](../../load_test/msa/PREREG.md) 「시나리오별 기준」 「후보 기준」 |
| I1–I7 | 불변식 | [running-session-server/consistency.md](running-session-server/consistency.md) 「불변식 I1–I7」 |
| F1–F9 · M0–M9 · 지울 기준 A · B | 분리 전 모놀리스 안 수정 (합치면 비교군 A′) · 옮기기 단계 · 옛 엔진 · 모놀리스 안 호스트를 지우는 기준 | [running-session-server/migration.md](running-session-server/migration.md) 「선행 수정 F1–F9」 「단계 M0–M9」 「지울 기준 A · B」 |
| E0–E7 | EKS 도입 단계 | [eks.md](eks.md) 「도입 단계 E0–E7」 |
| 넘기기-동기 · 넘기기-사본 | 넘기기 변형 | [running-session-server/architecture.md](running-session-server/architecture.md) 「넘기기」 |
| run n | 측정 회차 | [load_test/RESULTS.md](../../load_test/RESULTS.md) (첫 측정) · [load_test/msa/RESULTS.md](../../load_test/msa/RESULTS.md) 「회차 색인」 |

## 고치는 규칙

| 바뀐 것 | 고칠 문서 | 언제 |
|---|---|---|
| 결정 — 새로 정함 · 제안 확정 · 바꿈 | [decisions.md](decisions.md) 항목 먼저, 그다음 설계 문서 본문 | 같은 PR |
| 목표 구성 요소 | 이 문서 「구성 — 목표」 | decisions.md 항목과 같은 PR |
| test·prod에서 확인한 구성 | 이 문서 「구성 — 지금」 「환경」 「지금 운영 중인 것」, kafka · cdc · outbox-inbox | 그것을 내보내고 확인한 PR |
| 단계 진행 · 지금 막힌 것 | [running-session-server/README.md](running-session-server/README.md) 「진행 단계」 「지금 막힌 것」 | 단계가 바뀔 때 |
| 서비스 사이 계약 (이벤트 · WebSocket · REST) | api-spec 먼저 — 앱이 보는 변경은 iOS · Android 최소 버전 올림과 묶는다 | 코드보다 먼저 |
| 새 Kafka · Redis · DB 이름 | [platform.md](platform.md) 「새 이름」 → 만들면 [kafka.md](kafka.md) | 만들 때 |
| 서버 지표 | [metrics-convention.md](../metrics-convention.md) 예정 행 | 코드보다 먼저 |
| 판정 기준 | [load_test/msa/PREREG.md](../../load_test/msa/PREREG.md) — 얼린 뒤 바꾸면 RESULTS 「편차」에 적는다 | 첫 측정 전에 얼린다 |
| 측정 결과 | [load_test/msa/RESULTS.md](../../load_test/msa/RESULTS.md) — 첫 측정은 [load_test/RESULTS.md](../../load_test/RESULTS.md)를 run id로 가리킨다 | 회차마다 |
| 옮기기 단계마다 바뀌는 문서 | [running-session-server/migration.md](running-session-server/migration.md) 「단계마다 고칠 문서」의 목록 | 그 단계의 PR |
| 파일 더하기 · 옮기기 | 이 문서 머리말 표 · 「문서 규칙」 번호 표, CLAUDE.md · 루트 README 문서 색인 | 같은 PR |

- **절 이름은 링크 대상이다** — 링크에는 머리말 글자를 줄이지 않고 그대로 쓴다. 바꾸면 `grep -rn "「옛 이름」" docs load_test`로 링크를 함께 고친다.
- **저장소를 나눌 때 옮기는 것** — running-session-server/runtime.md(D-60)와, 러닝 DB 스키마를 정할 때 erd에 잠시 두는 러닝 DB 장뿐이다 — 스키마는 새 저장소의 Flyway가 이어받는다(D-56). 경계·일관성·검증·옮기기 문서와 결정 기록은 메인이 지킬 경계라 이 저장소에 남는다. 순서는 [running-session-server/README.md](running-session-server/README.md) 「저장소를 나눌 때」.
- **runtime.md는 파일로만 링크한다** — 절로 링크하지 않는다. 옮긴 뒤 그 자리에는 새 저장소를 가리키는 한 줄만 남긴다.
