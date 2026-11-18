# Outbox · Inbox

서비스 사이 이벤트를 보내고 받는 코드의 규칙 — 이벤트를 새로 보내거나 받는 코드를 쓰거나 고칠 때 이 규칙을 따른다.

| 무엇 | 어디 |
|---|---|
| 테이블 스키마 (정본) | [erd.md](../erd.md) `outbox_events` (inbox 테이블은 정하면 여기에 더한다) |
| 이벤트 계약 (토픽·본문 필드) | [api-spec.md](../api-spec.md) 9-5 |
| 아웃박스 행이 Kafka로 나가는 경로 | [cdc.md](cdc.md) — Debezium · PostgreSQL 논리 복제 |
| 토픽·계정·권한 | [kafka.md](kafka.md) |
| 코드 위치 | [architecture.md](../architecture.md) 패키지 구조 `infrastructure/kafka/` |

## 왜 쓰는가

저장과 이벤트 발행은 서로 다른 시스템(Postgres·Kafka)이라 한 트랜잭션으로 묶을 수 없다. 앱이 직접 보내면 둘 중 하나가 깨진다.

| 보내는 시점 | 깨지는 경우 | 결과 |
|---|---|---|
| 커밋 전에 보낸다 | 보낸 뒤 롤백된다 | 저장되지 않은 카드의 음악이 만들어진다 |
| 커밋 뒤에 보낸다 | 보내기 전에 발행이 실패하거나 인스턴스가 죽는다 | 이벤트가 사라져 음악이 `GENERATING`에 영영 머문다 |

- **아웃박스(보내는 쪽)**: 이벤트를 Kafka가 아니라 **같은 DB의 테이블에, 저장과 같은 트랜잭션으로** 쓴다. 저장과 이벤트가 함께 커밋되거나 함께 롤백된다. 커밋된 행을 Kafka로 옮기는 것은 CDC가 맡는다.
- **Inbox(받는 쪽)**: CDC와 Kafka는 **최소 한 번** 전달이다 — 같은 이벤트가 두 번 올 수 있다. 받는 쪽은 처리한 이벤트를 기록해 두 번 처리하지 않는다.

## 흐름

```text
[running-service]                                     [AI 서버]
 9-2 확정 트랜잭션
   ├ record_cards · record_card_images INSERT
   └ outbox_events INSERT ──┐  (함께 커밋)
                            │
                   WAL → Debezium (cdc.md)
                            │
                            ▼
              {env}.record-card.music.requested ──▶ inbox로 중복 거름 → 음악 생성
                                                              │
 inbox로 중복 거름 → 음악 상태 변경  ◀── {env}.record-card.music.generated
 (완료 구독 — 아직 구현 전)
```

## 보내는 쪽 — 아웃박스

### 언제 쓰는가

- **이벤트를 만든 저장과 같은 트랜잭션에서 쓴다** — 포트는 `@Transactional` 메서드 안에서 부른다. S3 확인처럼 트랜잭션 밖에서 도는 단계에서 부르지 않는다. 지금은 `RecordCardDraftSaver.save()`다.
- **실제로 새로 저장했을 때만 쓴다** — 재시도·이미 처리된 요청·409로 끝나는 경로는 쓰지 않는다. 9-2는 카드 저장 결과가 `CREATED`일 때만 부르고, 확정된 초안을 다시 부른 재시도는 저장 전에 돌아가 이벤트를 다시 쓰지 않는다.
  - 이 규칙이 앱 쪽 중복을 막는다. 이벤트 ID는 막지 못한다 — 다시 쓰면 새 ID를 가진 다른 이벤트가 된다.
- **앱은 Kafka로 직접 보내지 않는다** — 커밋 뒤 직접 발행도, 아웃박스와 직접 발행을 함께 쓰는 것도 하지 않는다. running-service에는 Kafka 프로듀서가 없다.

### 어떻게 쓰는가

- **애플리케이션은 아웃박스를 모른다** — 포트는 업무 언어로 짓는다(`RequestRecordCardMusicPort.request()` — "음악을 요청한다"). 아웃박스 행으로 바꾸는 것은 `infrastructure/kafka/outbox/`의 어댑터다.
- **이벤트 ID는 어댑터가 UUIDv7로 정한다** — DB가 매기지 않는다. Kafka 헤더 `id`로 나가 받는 쪽이 중복을 거르는 키가 된다. CDC가 같은 행을 다시 보내도 ID는 같다.
- **`aggregate_type` · `aggregate_id`는 이벤트의 대상이다** — `aggregate_id`가 Kafka key가 되어, 같은 대상(카드)의 이벤트는 같은 파티션으로 가고 순서가 지켜진다.
- **토픽 이름은 `KafkaNamingProperties.topic(name)`으로 만든다** — 환경 접두어(`dev`·`test`·`prod`)를 문자열로 직접 붙이지 않는다. 어댑터에는 접두어 없는 이름만 상수로 둔다(`record-card.music.requested`).
- **본문(payload)은 계약대로 직접 조립한다** — 도메인 객체나 DTO를 통째로 직렬화하지 않는다. 필드 이름이 코드 리네임에 따라 바뀌면 받는 쪽이 깨진다.
  - 키 이름은 계약이 정한 형식이다(요청 이벤트는 받는 쪽에 맞춘 `snake_case`).
  - **값이 없어도 키를 빼지 않고 `null`로 둔다** — `LinkedHashMap`을 쓴다(`Map.of`는 null을 받지 않는다).
  - 시각처럼 형식이 갈리는 값은 어댑터에서 형식을 정해 문자열로 넣는다(요청 이벤트의 `start_at`은 `+09:00`, 초까지).
- **행은 한 번 쓰고 고치지 않는다** — 앱은 이 테이블을 조회·수정·삭제하지 않는다. 발행 상태 컬럼도 없다(어디까지 발행됐는지는 복제 슬롯으로 본다 — [cdc.md](cdc.md)).

### 테이블

정본은 [erd.md](../erd.md) `outbox_events`다. 코드를 쓸 때 알아야 할 것만 옮긴다.

- 이벤트 종류마다 테이블을 나누지 않는다 — 한 테이블에 `aggregate_type`·`topic`으로 구분한다.
- **`topic`·`aggregate_type`은 CHECK로 허용 값을 막는다** — 이벤트를 늘리면 식을 넓혀야 저장된다. 엔티티는 세 환경 토픽을 모두 허용하고, **배포 DB(test·prod)는 그 환경 토픽만** 허용한다 — 손으로 만든 DDL이라 이벤트를 늘릴 때 함께 넓힌다.
- FK·인덱스를 두지 않는다. 행은 지우지 않고 남긴다(보관 기간은 아직 정하지 않았다).

## 받는 쪽 — Inbox

> **아직 구현 전이다** — running-service가 완료 토픽(`{env}.record-card.music.generated`)을 구독할 때 함께 만든다. 아래 "정해진 것"은 api-spec 9-5에 적힌 것이고, "처리 순서"는 제안이다.

AI 서버로 가는 요청 이벤트의 inbox는 **AI 서버가 맡는다.** running-service는 거를 키(헤더 `id`)를 실어 보낼 뿐이다.

### 정해진 것

- **같은 이벤트가 다시 온다고 전제한다** — CDC 재시작, 컨슈머 리밸런스, 오프셋 커밋 전 장애 모두 같은 이벤트를 다시 준다.
  - 프로듀서 중복 방지(`enable.idempotence`)를 켜도 마찬가지다 — 그것은 프로듀서가 한 번 붙어 있는 동안의 재전송만 막는다. 위 경우는 막지 못한다.
- **running-service 전체가 컨슈머 그룹 하나로 받는다** — ALB 뒤 인스턴스가 여럿이어도 이벤트 하나는 한 인스턴스만 처리한다. 처리 결과는 DB에만 쓰고 인스턴스 메모리에 두지 않는다 — 어느 인스턴스가 받아도 같다.
- **처리하지 못하면 오프셋을 커밋하지 않는다** — 다시 받아 처리한다.
- **inbox와 상태 규칙은 둘 다 둔다** — 막는 것이 다르다.

  | | 막는 것 | 예 |
  |---|---|---|
  | inbox | 같은 **이벤트**를 두 번 처리하는 것 | 같은 완료 이벤트가 두 번 왔다 |
  | 상태 규칙 (`GENERATING`인 행만 바꾼다) | 같은 **음악**의 상태가 두 번 바뀌는 것 | 다른 이벤트가 이미 끝난 음악을 다시 가리킨다 |

- **대상 행이 없으면 그 결과만 건너뛰고 로그를 남긴다** — 이미 지운 카드·수정에서 뺀 사진의 늦은 완료다. 오류로 보고 재시도하지 않는다.

### 처리 순서 (제안)

1. 트랜잭션을 연다.
2. inbox에 이벤트 키를 INSERT한다 — 키가 PK라 이미 있으면 충돌한다. **충돌하면 처리된 이벤트다** — 아무것도 바꾸지 않고 커밋한다.
3. 결과마다 대상 행을 찾아 상태를 바꾼다(상태 규칙을 따른다).
4. 커밋한다 — inbox 기록과 상태 변경이 함께 커밋되거나 함께 롤백된다.
5. 커밋한 뒤 오프셋을 커밋한다 — 4와 5 사이에 죽으면 다시 받지만 2에서 걸러진다.

### 정하지 않은 것

1. **무엇으로 거르는가** — 완료 이벤트는 AI 서버가 발행해 우리 아웃박스 ID가 없다. AI 서버가 정한 완료 이벤트 형식에는 `inference_id`가 있다 — 키 후보다. Kafka 헤더로 이벤트 ID를 주는지도 AI 서버와 확인한다.
2. **inbox 스키마** — 컬럼·보관 기간·지난 행 정리. 정하면 erd에 더한다.
3. **처리할 수 없는 메시지** — 본문을 읽지 못하거나 계약에 맞지 않는 메시지는 다시 받아도 계속 실패해 그 파티션이 멈춘다. 따로 모을지(DLT)와 언제 넘길지 정한다.
4. **Kafka 클라이언트 의존성** — running-service에는 아직 없다. 완료 구독을 만들 때 들어온다.

## 지금 있는 이벤트

| 토픽 (`{env}.` 생략) | 방향 | 쓰는 곳 | 코드 | 받는 쪽 중복 거르기 |
|---|---|---|---|---|
| `record-card.music.requested` | running-service → AI | 9-2 작성 확정 (9-3 수정은 그 PR에서 더한다) | `RecordCardDraftSaver` → `RequestRecordCardMusicPort` → `RecordCardMusicOutboxAdapter` | AI 서버 inbox — 헤더 `id` (제안, 완료 구독 구현 뒤 AI 서버와 확인) |
| `record-card.music.generated` | AI → running-service | — | 아직 없음 | running-service inbox (위 "정하지 않은 것" 1) + 상태 규칙 |

## 새 이벤트를 더할 때

**보내는 쪽**

- [ ] api-spec에 계약을 적는다 — 토픽, 방향, 단위, key, 본문 필드
- [ ] 업무 언어로 포트를 만들고, `infrastructure/kafka/outbox/`에 어댑터를 둔다
- [ ] 포트를 부르는 곳이 저장과 같은 트랜잭션이고, 새로 저장했을 때만 부르는지 확인한다
- [ ] erd `outbox_events`의 CHECK를 넓힌다 — `ck_outbox_event_topic`, 새 대상이면 `ck_outbox_event_aggregate_type`과 `AggregateType`. **test·prod DB의 CHECK도 손으로 함께**(각 환경 토픽만)
- [ ] Kafka에 토픽을 만들고 권한을 준다 ([kafka.md](kafka.md))
- [ ] CDC 설정이 바뀌는지 확인한다 ([cdc.md](cdc.md))
- [ ] 테스트 — 아래 "테스트"의 세 층

**받는 쪽**

- [ ] 받는 서비스의 inbox로 거를 키를 정해 계약에 적는다
- [ ] 컨슈머 그룹과 읽기 권한을 정한다 ([kafka.md](kafka.md))

## 테스트

| 층 | 무엇을 보는가 | 예 |
|---|---|---|
| 단위 | 어댑터가 계약대로 행을 만드는가 — 토픽(접두어), key, 본문 키 이름·null·시각 형식 | `RecordCardMusicOutboxAdapterTest` |
| 단위 | 새로 저장했을 때만 포트를 부르는가 — 재시도·409·실패 경로에서는 부르지 않는다 | `RecordCardDraftSaverTest` |
| 통합 | 흐름 안에서 요청이 남는가 — 포트를 메모리 대역으로 바꾼다 | `InMemoryRecordCardMusicRequests` |
| E2E | 실제 DB에 행이 남는가 — 실패한 요청은 0줄, 성공한 요청은 1줄 | `RecordCardE2eTest` |

- **테스트가 통과해도 검증되지 않는 것** — CDC가 실제로 발행하는지, 헤더·key·토픽이 컬럼대로 나가는지, 배포 DB의 CHECK. E2E 스택에는 Kafka·Debezium이 없다 — 직접 확인하는 항목은 [cdc.md](cdc.md) 「검증」.
