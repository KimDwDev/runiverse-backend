# AI 전용 서버 — 구조

메인 서버(running-service)에서 본 메인 ↔ AI 경계를 적는다 — 누가 어떤 데이터를 갖는지, 함께 쓰는 것, 음악 흐름, 완료 발행의 지금과 도표, AI가 도는 곳, 러닝 세션 분리가 이 경로에 주는 영향. 계약은 [api-spec.md](../../api-spec.md) 9-5가 정본이라 옮기지 않고 링크한다.

> 상태: 절마다 적는다 — 「경계」「함께 쓰는 것」「흐름 — 기록 카드 음악」「계약」은 운영 중, 「완료 발행 — 지금과 그림」은 지금 운영 중 · 도표 쪽 정하지 않음, 「실행 환경」은 AI 쪽 · 제안, 「러닝 세션 분리의 영향」은 정함 (D-24) · 세부 정하지 않음. 계약이 바뀌면 api-spec 9-5를 먼저 고치고, 이 문서는 AI 쪽 확인 뒤에 고친다.

| 무엇 | 어디 |
|---|---|
| 지금 어디까지 되는가 · AI 쪽에 기다리는 것 | [README.md](README.md) 「지금 상태」 「기다리는 것」 |
| 왜 따로인가 | [rationale.md](rationale.md) |
| 세 서비스 전체 그림 | [msa README.md](../README.md) 「구성 — 지금」 「구성 — 목표」 |
| 결정 | [decisions.md](../decisions.md) D-03 · D-06 · D-07 · D-14 |

> 주소·키·비밀값은 적지 않는다 — Parameter Store 이름까지만.

## 경계

**상태** — 운영 중 ([decisions.md](../decisions.md) D-03)

- **두 방향 모두 Kafka다** — 메인은 AI 서버도 SageMaker도 부르지 않는다. 서비스 사이 동기 호출 허용 목록([running-session-server/architecture.md](../running-session-server/architecture.md) 「동기 호출 허용 목록」, D-52)에 AI 쪽 호출은 없다.
- **데이터는 따로 갖는다** — AI는 메인 DB를 읽지 않는다. 필요한 값은 요청 이벤트에 실린다(`running_summary`는 메인이 발행할 때 러닝 기록에서 읽어 싣는다). 메인도 AI 저장소를 모른다.
- **메인이 정본인 것** — 사용자·인증·알림·기록 카드, 음악마다의 상태(`GENERATING`·`COMPLETED`·`FAILED`)와 받은 key.
- **AI가 맡는 것** — 음악 생성(모델·추론), 요청 중복 거르기(자기 inbox), 음악 S3 key 정하기(D-07), 완료 발행. 저장소는 AI 쪽 것이다(지금 inbox 포함. 도표에서는 MySQL AI DB — inbox·생성 상태·아웃박스).
- **생성·수정·다시 만들기는 메인이 가른다** — AI는 요청마다 같은 방식으로 만든다. 메인이 보내기 전에 다시 만들 음악만 `GENERATING`으로 되돌린다(api-spec 9-5, D-15·D-16).
- 서비스 전체의 맡는 것·갖는 데이터 표는 [msa README.md](../README.md) 「누가 무엇을 맡는가」.

## 함께 쓰는 것

**상태** — 운영 중

- **S3뿐이다.**

  | 무엇 | 쓰는 쪽 | 읽는 쪽 |
  |---|---|---|
  | 카드 사진 (기록 카드 버킷) | 앱이 메인이 준 URL로 올린다 | AI — 요청의 `image_key`로 직접 읽는다 |
  | 음악 (음악 버킷, `S3_MUSIC_BUCKET`) | AI — key를 정해 올리고 완료 이벤트로 알린다 | 메인 — 받은 key를 그대로 저장하고 보기 URL만 서명한다 (D-14) |

- 버킷 이름·권한은 [platform.md](../platform.md) 「S3」. 음악 버킷 이름은 아직 받지 못했다([README.md](README.md) 「정하지 않은 것」 1).

## 흐름 — 기록 카드 음악

**상태** — 운영 중 — 5단계 반영은 AI 쪽 헤더 `id`를 기다린다([README.md](README.md) 「지금 상태」)

1. 앱이 카드를 확정·수정하거나 음악을 다시 만들게 한다(9-2 · 9-3 · 9-6) — 카드·음악 상태(`GENERATING`)와 아웃박스 행이 한 트랜잭션에 커밋된다.
2. Debezium이 WAL에서 그 행을 읽어 요청 토픽에 발행한다.
3. AI 서버가 inbox로 중복을 거르고 음악을 만들어 S3에 올린다.
4. AI 서버가 요청마다 최종 결과를 완료 토픽에 한 번 발행한다.
5. 메인 한 대가 받아 inbox로 거르고 음악 상태를 바꾼다.

그림은 [outbox-inbox.md](../outbox-inbox.md) 「흐름」, 단계마다의 코드는 같은 문서 「지금 있는 이벤트」.

## 계약

**상태** — 운영 중 — 링크만 둔다

| 무엇 | 어디 |
|---|---|
| 토픽 · key · 헤더 · 본문 필드 · 받는 쪽 경우별 처리 | [api-spec.md](../../api-spec.md) 9-5 |
| 계약에서 아직 정하지 않은 것 | api-spec 9-5 「아직 정하지 않은 것」 |
| 보내기 · 받기 코드 규칙, 처리할 수 없는 메시지 | [outbox-inbox.md](../outbox-inbox.md) 「보내는 쪽 — 아웃박스」 「받는 쪽 — Inbox」 |
| 이벤트를 더할 때 | [outbox-inbox.md](../outbox-inbox.md) 「새 이벤트를 더할 때」 |
| 토픽 · 계정 · 그룹 이름과 권한 | [kafka.md](../kafka.md) 「이름」 「ACL」 |
| 로컬에서 AI 없이 돌리기 | [outbox-inbox.md](../outbox-inbox.md) 「로컬 가짜 AI (음악 완료)」 (D-13) |

## 완료 발행 — 지금과 그림

**지금** — 운영 중. AI 서버가 완료 토픽에 직접 발행한다. 계정 `{env}.runiverse-ai`가 `generated`에 `WRITE`를 갖는다([kafka.md](../kafka.md) 「ACL」). Kafka가 멈춘 동안의 완료 이벤트는 AI 서버가 다시 보내야 한다([kafka.md](../kafka.md) 「노드가 하나라서」).

**그림** — 정하지 않음. 11-24 시스템 구성도는 AI DB(MySQL) 아웃박스 → Debezium → 완료 토픽이다. 그쪽으로 가면 바뀌는 것:

| | 지금 | 도표대로 가면 |
|---|---|---|
| 발행하는 것 | AI 서버의 Kafka 프로듀서 | Debezium MySQL 커넥터 (binlog) |
| `generated` 쓰기 권한 | `{env}.runiverse-ai` | Debezium 계정으로 옮긴다 — 이름은 정할 때 [platform.md](../platform.md) 「새 이름」 |
| Kafka가 멈췄을 때 | AI 서버가 다시 보낸다 | 아웃박스 행이 남아 커넥터가 이어 보낸다 |
| 메인 쪽 영향 | — | [api-spec.md](../../api-spec.md) 9-5 「나중에 다시 볼 것」 셋째 항목 |
| CDC 문서 | — | [cdc.md](../cdc.md)는 Postgres만 다룬다 — MySQL 커넥터의 주인이 필요하다 (「정하지 않은 것」 1) |

## 실행 환경

AI 쪽이 정한다. 메인이 아는 것과 플랫폼 설계에서 권한 것만 적는다.

| 무엇 | 내용 | 상태 |
|---|---|---|
| 모델 | Qwen3-VL → ACE-Step 1.5 | AI 쪽 |
| 추론 | SageMaker 비동기 호출 — 완료 이벤트 `invoke.http_status` 202 | AI 쪽 |
| 추론 인스턴스 | ml.g6.xlarge, 최소 0대에서 밀린 요청이 생기면 늘린다 — 같은 급 g5보다 시간당 싸고, 실시간 엔드포인트는 쉬는 동안에도 요금이 나간다. 모델이 더 큰 인스턴스를 요구할 수 있다 | 제안 — AI 쪽이 정한다. 비용도 AI 쪽이다 — [eks.md](../eks.md) 「비용」에 넣지 않았다 |
| Python 워커 | 우리 EKS의 `ai-server` 워크로드 후보 ([eks.md](../eks.md) 「워크로드」) | 제안 (D-41) — 우리 EKS에서 돌릴지는 정하지 않음, 단계 E6 ([eks.md](../eks.md) 「도입 단계 E0–E7」) |
| AI DB | 도표: MySQL (inbox · 생성 상태 · 아웃박스) | 자리는 정하지 않음 — RDS는 서비스 DB · 러닝 DB 둘뿐이다 (D-48, D-40의 RDS MySQL을 바꿈) |

## 러닝 세션 분리의 영향

**상태** — 정함 ([decisions.md](../decisions.md) D-24) — 러닝 기록의 정본은 러닝 DB이고 메인은 사본을 갖는다. 사본 모양과 날씨 자리는 정하지 않음.

- **9-5 계약은 그대로다** — AI는 계속 `running_summary`를 이벤트로 받고, 러닝 DB도 읽지 않는다.
- **메인이 기록을 읽는 곳이 메인 사본 테이블로 바뀐다** — 지금은 `running_records`를 직접 읽는다(`RunningRecordPersistenceAdapter`).

  | 메인 코드 | 지금 읽는 것 | 분리 뒤 |
  |---|---|---|
  | 9-1 `CreateRecordCardDraftHandler` → `ExistsOwnRunningRecordPort` | 기록이 있고, 내 것이고, 숨긴 방의 기록이 아니다 | 사본에 반영된 뒤에만 통과한다 — 그 전에는 없는 기록(404) |
  | 9-2 `RecordCardDraftSaver` → `LockOwnRunningRecordPort` · `LoadRunningSummaryPort` | 기록 행을 `PESSIMISTIC_WRITE`로 잠그고 `running_summary` 여덟 값을 읽는다 | 사본 행을 잠그고 읽는다 — 잠글 대상은 사본 모양과 함께 정한다 |
  | 9-3 `RecordCardEditor` · 9-6 `RetryRecordCardMusicHandler` → `LoadRunningSummaryPort` | `running_summary` 여덟 값 | 사본에서 읽는다 |

- **사본에 있어야 하는 것** — `running_summary` 여덟 값(api-spec 9-5 요청 이벤트)과 숨긴 방 여부(9-1 · 9-2가 본다). 사본 행이 없으면 9-2는 404, 9-3 · 9-6은 `IllegalStateException`(500)으로 끝나 음악 요청이 나가지 않는다. 빠진 행은 대조로 잡는다([running-session-server/consistency.md](../running-session-server/consistency.md) 「대조」).
- **끝낸 직후에는 카드를 만들 수 없다** — 메인 사본에 반영되기 전까지다. 기록과 카드는 반영 뒤에 보인다(D-58). 같은 창의 다른 영향은 [running-session-server/consistency.md](../running-session-server/consistency.md) 「종료 뒤 최종 일관 창」(D-31 제안).
- **날씨 값이 바뀔 수 있다** — 지금은 끝내기 안에서 채운다(`RunningFinisher` → `LoadWeatherPort`). 끝내기 경로에서 빼는 것은 제안(D-32)이고, (가) 세션이 시작 때 받기와 (나) 메인이 반영 뒤 채우기 중 (나)면 날씨가 비어 있는 동안 9-2가 올 수 있다. `weather_code` · `temperature`는 9-5의 필수 필드이고, 지금 `RecordCardMusicRequest.RunningSummary.weatherCode`는 `int`라 빈 값을 받지 못한다.
- 사본 모양(지금 테이블 그대로, D-68)과 날씨 자리(D-63)는 정했다 — 근거는 [running-session-server/architecture.md](../running-session-server/architecture.md) 「메인 사본과 반영」 「정하지 않은 것」. 이 절은 그것이 정해지는 단계에서 고친다([running-session-server/migration.md](../running-session-server/migration.md) 「단계마다 고칠 문서」).

## 정하지 않은 것

1. **AI 완료 발행을 AI 아웃박스로 바꿀지와 그 주인** — 도표대로 가면 MySQL 커넥터를 누가 운영하는지(AI 쪽 vs cdc.md를 넓혀 우리 Connect), `generated` 쓰기 권한을 어느 계정으로 옮길지 함께 정한다(「완료 발행 — 지금과 그림」).
2. **AI DB 자리** — D-48의 RDS 둘에 들지 않는다. AI 쪽이 따로 운영할지, 우리 플랫폼 어디에 둘지 → [platform.md](../platform.md) 「정하지 않은 것」.
3. **Python 워커를 우리 EKS에서 돌릴지 (워크로드 주인)** → [eks.md](../eks.md) 「정하지 않은 것」.
4. ~~분리 뒤 `running_summary`의 재료~~ — **정했다 → D-68 · D-63**: 지금 테이블 그대로 읽고, 날씨 값도 지금과 같다 — [running-session-server/architecture.md](../running-session-server/architecture.md) 「정하지 않은 것」.
