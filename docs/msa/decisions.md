# 결정 기록

메인 서버(running-service)·러닝 세션 서버·AI 전용 서버와 그 아래 플랫폼에 대해 정한 것, 버린 것, 뒤에 바뀐 것을 한곳에 날짜순으로 남긴다. 범위가 서로 얽혀 있어(예: 예산 결정이 EKS 데이터 배치를 뒤집는다) 서비스마다 따로 두지 않는다. 설계 문서는 지금 설계만 보여 주고, 왜 그렇게 됐는지는 이 기록의 항목으로 링크한다.

| 무엇 | 어디 |
|---|---|
| 전체 지도 · 서비스 이름 · ID 규칙 | [README.md](README.md) 「서비스 이름」 「문서 규칙」 |
| 러닝 세션 분리 — 이유 · 구조 · 검증 · 옮기기 | [running-session-server/README.md](running-session-server/README.md) |
| 단계 진행 상태 | [running-session-server/README.md](running-session-server/README.md) 「진행 단계」 |
| AI 전용 서버 | [ai-server/README.md](ai-server/README.md) |
| 데이터 · 인프라 / EKS | [platform.md](platform.md) · [eks.md](eks.md) |

> 주소·키·비밀값은 적지 않는다 — Parameter Store 이름까지만.

- **상태** — `정함`: 팀이 정했다. `제안`: 설계 검토에서 권한 안이고 아직 확정하지 않았다 — 확정하면 상태 줄만 `정함`으로 바꾼다. `바뀜 → D-yy`: 뒤 항목이 바꿨다. `버림`: 대신할 결정 없이 접었다. 상태 줄의 `정하지 않음`은 그 결정이 일부러 남긴 빈칸이고, 답은 링크한 문서의 「정하지 않은 것」에서 기다린다.
- **번호는 다시 쓰지 않는다** — 바뀐 항목도 지우지 않는다. 본문은 고치지 않고 상태 줄만 고친다(날짜와 양쪽 링크).
- **러닝 세션 저장소의 자기 기록은 `RS-D-01`부터** — 저장소를 나눈 뒤 세션 안에서만 뜻이 있는 결정(내부 구조·라이브러리)은 그 저장소에 `RS-D-` 번호로 적는다. 서비스 사이·플랫폼 결정은 계속 여기에 `D-` 번호로 적는다.
- **범위** — `AI` · `세션` · `메인` · `플랫폼` · `공통`(서비스 둘 이상) · `문서`.

## 적는 법

```text
### D-nn 제목 (YYYY-MM-DD)

`범위` · **상태** — 상태에 붙는 한 줄 (바꾼 항목, 정하지 않은 부분)
- **결정** — 고른 것. 바뀌는 표(키 목록·호출 목록·임계값)는 옮기지 않고 그 표의 문서에 링크한다
- **버린 대안** — 대안 — 버린 이유 (대안마다)
- **이유** — 근거 문서의 「절」. 숫자는 run id·PR·커밋으로 가리킨다
- **영향** — 이 결정으로 고치는 문서
```

- 결정을 먼저 적고, 같은 PR에서 설계 문서를 고친다.
- 다른 항목을 바꾸면 양쪽 상태 줄에 서로를 적는다 — 바뀐 쪽 `바뀜 → D-yy`, 바꾼 쪽 `D-xx를 바꾼다`.
- 임계값은 [load_test/msa/PREREG.md](../../load_test/msa/PREREG.md)에만, 측정 숫자는 RESULTS 파일에만 둔다.
- 버린 대안 · 영향이 없으면 그 줄을 쓰지 않는다.

## 색인

| D | 날짜 | 범위 | 한 줄 | 상태 |
|---|---|---|---|---|
| D-01 | 10-15 | 플랫폼 | 러닝 실시간 상태는 Redis EC2 + AOF | 정함 |
| D-02 | 11-08 | AI | 음악 S3 key는 서버가 정해 요청 | 바뀜 → D-07 |
| D-03 | 11-09 | AI | 메인과 AI는 Kafka 양방향으로만 | 정함 |
| D-04 | 11-09 | 공통 | Kafka 이름은 `KAFKA_ENV` 하나로, SASL_SSL + SCRAM + ACL | 정함 |
| D-05 | 11-12 | AI | 기록 카드 PR을 셋으로 | 정함 |
| D-06 | 11-12 | 공통 | 이벤트는 아웃박스 + CDC로 보낸다 | 정함 |
| D-07 | 11-13 | AI | 카드·사진 ID UUIDv7, 음악 key는 AI가 정한다 | 정함 |
| D-08 | 11-16 | 문서 | MSA 문서는 docs/msa, 설정은 infra/kafka | 정함 |
| D-09 | 11-16 | AI | AI 서버 inbox는 헤더 `id`로 거른다 | 제안 |
| D-10 | 11-17 | 플랫폼 | test는 Kafka·Postgres·Connect를 한 EC2에 | 정함 |
| D-11 | 11-19 | AI | 완료 inbox 키 `inference_id`, `GENERATING` 규칙 | 바뀜 → D-12 |
| D-12 | 11-20 | 메인 | 헤더 `id` 없으면 건너뜀, 잠깐 생긴 DB 문제만 재시도 | 정함 |
| D-13 | 11-22 | 메인 | 로컬은 가짜 AI 완료로 | 정함 |
| D-14 | 11-23 | AI | 음악은 별도 버킷 `S3_MUSIC_BUCKET` | 정함 |
| D-15 | 11-23 | AI | 기본곡은 어떤 결과든 따르고 사진 음악은 `GENERATING`일 때만 | 정함 |
| D-16 | 11-23 | AI | 9-6은 `FAILED`인 음악만 다시 요청 | 정함 |
| D-17 | 11-24 | 공통 | 4단계 계획 | 정함 |
| D-18 | 11-24 | 세션 | DB 없는 실시간 가장자리만 떼기 | 바뀜 → D-24 |
| D-19 | 11-24 | 플랫폼 | EKS는 미루고 두 함대부터 | 바뀜 → D-23 |
| D-20 | 11-24 | 세션 | 모듈러 모놀리스가 끝 상태, 분리는 조건부 | 바뀜 → D-22, D-45 |
| D-21 | 11-24 | 세션 | ALB 가중치 카나리, 100% 2주 뒤 삭제 | 바뀜 → D-36, D-37 |
| D-22 | 11-24 | 세션 | 지우지 않고 모놀리스 안에 새로 만든다 | 정함 |
| D-23 | 11-24 | 플랫폼 | EKS는 목표다 | 정함 |
| D-24 | 11-24 | 공통 | 러닝 기록의 정본은 러닝 DB, 메인은 사본 | 정함 |
| D-25 | 11-24 | 세션 | 새 k6로 반증, 옛 결과는 기준선만 | 정함 |
| D-26 | 11-24 | 공통 | 경계는 생애 단계로 | 정함 |
| D-27 | 11-24 | 메인 | 방 번호와 활성 신청은 메인이 단독으로 | 제안 |
| D-28 | 11-24 | 공통 | 결과 이벤트 셋 + 사용자 생애 이벤트 | 제안 |
| D-29 | 11-24 | 공통 | 예약 작업은 종류별로 주인을 나눈다 | 제안 |
| D-30 | 11-24 | 공통 | JWT ES256 — 메인 서명, 세션 검증만 | 제안 |
| D-31 | 11-24 | 메인 | 끝낸 뒤 메인 반영 전까지의 창을 받아들인다 | 제안 |
| D-32 | 11-24 | 공통 | 날씨 조회를 끝내기 경로에서 뺀다 | 바뀜 → D-63 |
| D-33 | 11-24 | 세션 | k6 첫 설계(v1) — 성능 중심 | 바뀜 → D-57 |
| D-34 | 11-24 | 세션 | 끝내기 결함은 옛 엔진에서 먼저 고친다 (F1–F9) | F1 바뀜 → D-63 · F2–F9 제안 |
| D-35 | 11-24 | 세션 | 새 엔진은 Gradle 서브프로젝트 | 제안 |
| D-36 | 11-24 | 세션 | 엔진은 방마다 정하고 바꾸지 않는다 | 제안 |
| D-37 | 11-24 | 세션 | 지울 기준 A · B | 제안 |
| D-38 | 11-24 | 공통 | 러닝 중 탈퇴는 503으로 거절 | 바뀜 → D-43 |
| D-39 | 11-24 | 세션 | 모노레포에 배포물 둘 | 바뀜 → D-47 |
| D-40 | 11-24 | 플랫폼 | EKS 종합안의 데이터 배치 | 바뀜 → D-48, D-49, D-50 |
| D-41 | 11-24 | 플랫폼 | EKS 앱 쪽 설계 | 제안 |
| D-42 | 11-24 | 세션 | 계획 종료는 닫기 코드 4500을 다시 쓴다 | 제안 |
| D-43 | 11-24 | 공통 | 러닝 중 계정 탈퇴는 saga로 | 정함 |
| D-44 | 11-24 | 공통 | 탈퇴 saga의 단계 | 제안 |
| D-45 | 11-24 | 공통 | 나누는 이유는 안정성 | 정함 |
| D-46 | 11-24 | 메인 | 결과 조회는 메인이 응답 | 바뀜 → D-58 |
| D-47 | 11-24 | 세션 | 러닝 세션 서버는 별도 저장소 | 정함 |
| D-48 | 11-24 | 플랫폼 | 예산 절약 배치 — Kafka·Redis EC2, RDS 둘, EKS는 앱만 | 정함 |
| D-49 | 11-24 | 플랫폼 | Redis는 EC2 + AOF, 서비스마다 한 대 | 정함 |
| D-50 | 11-24 | 플랫폼 | Kafka는 당분간 EC2 한 대 | 정함 |
| D-51 | 11-24 | 공통 | 결과가 Kafka를 타기 전에 DLT·건너뜀 알람·대조 | 정함 |
| D-52 | 11-24 | 공통 | 서비스 사이 동기 호출은 허용 목록만 | 정함 |
| D-53 | 11-24 | 공통 | 허용 목록 후보와 공통 규칙 | 제안 |
| D-54 | 11-24 | 공통 | 모듈 경계는 check_conventions.py로 | 정함 |
| D-55 | 11-24 | 공통 | 추적은 OTel Java 에이전트 | 정함 |
| D-56 | 11-24 | 세션 | 러닝 DB 스키마는 Flyway | 정함 |
| D-57 | 11-24 | 세션 | k6 2차 설계(v2) — 러닝 연속성 | 제안 |
| D-58 | 11-24 | 세션 | 결과 조회는 세션이 응답 | 정함 |
| D-59 | 11-24 | 문서 | 문서 계획 v1 · v2 · v3 | 바뀜 → D-60 |
| D-60 | 11-24 | 문서 | docs/msa 구조와 이름 | 정함 |
| D-61 | 11-24 | 공통 | 러닝 세션 서버의 실제 이름 | 제안 |
| D-62 | 11-24 | 세션 | 저장소 옮기기(M6)를 운영 추출(M7) 앞에 | 제안 |
| D-63 | 11-25 | 세션 | 끝내기는 새 엔진에서 — 잠금 밖 S3 · 날씨, 짧은 트랜잭션 | 정함 |
| D-64 | 11-25 | 세션 | 끝내기 폭주는 속도가 아니라 격리로 잰다 + 매칭↔러닝 정합성 | 정함 |

## 분리 전 결정 (10-15 ~ 11-23)

### D-01 러닝 실시간 상태는 Redis EC2 + AOF (2026-10-15)

`플랫폼` · **정함** — D-49가 서비스마다 한 대로 이어받는다
- **결정** — 러닝 중 상태(트랙·거리·상태·콤보)는 EC2 Redis에 두고 AOF(`appendonly yes`, `appendfsync everysec`)를 켠다. RDS 상태 어댑터 4개(`infrastructure/persistence/running/state/*DbAdapter`)는 `@Profile("state-db")`로 꺼 둔 채 남긴다(a0cc1aaf, 10-17). 운영 Redis에는 10-23에 적용했다.
- **버린 대안** — 상태를 RDS로 옮기기 — pub/sub 때문에 Redis는 어차피 남아 비용이 줄지 않고, load-1000에서 커넥션 풀을 다 썼다(run 53) / AOF 없이 두기 — Redis를 죽이자 러닝 상태가 에러 없이 사라졌다(run 16)
- **이유** — [load_test/RESULTS.md](../../load_test/RESULTS.md) 「결론」, 「AOF 적용 후 재검증 (run 18)」
- **영향** — load_test/RESULTS.md 「권장」, [platform.md](platform.md) 「Redis」

### D-02 음악 S3 key는 서버가 정해 요청한다 (2026-11-08)

`AI` · **바뀜 → D-07**
- **결정** — 기록 카드 음악은 AI가 만든다. 서버가 음악 S3 key를 정해 요청에 싣고, AI가 그 key에 올린 뒤 완료를 알린다. 음악마다 `PENDING`·`READY`·`FAILED`.
- **이유** — 기록 카드 도메인 첫 설계
- **영향** — api-spec 8·9장, erd `record_card*`

### D-03 메인과 AI는 Kafka 양방향으로만 주고받는다 (2026-11-09)

`AI` · **정함**
- **결정** — 요청 토픽과 완료 토픽 두 방향 모두 Kafka다. 메인은 AI 서버나 SageMaker를 직접 부르지 않는다.
- **버린 대안** — 백엔드가 S3에 올리고 `InvokeEndpointAsync` + SNS로 결과를 받는 잠정안 — 메인이 AI 추론 경로를 직접 다루게 된다
- **이유** — 요청 하나가 여러 곡을 오래 만들고, 주인과 런타임이 다르며, 한쪽이 멈춰도 다른 쪽이 돈다. 서버가 나뉠 수 있어 잠시 보류했다가 같은 날 정했다 ([ai-server/rationale.md](ai-server/rationale.md) 「긴 비동기 생성」)
- **영향** — api-spec 9-5, [README.md](README.md) 「통신 — 어디로 무엇이 지나가는가」

### D-04 Kafka 이름은 `KAFKA_ENV` 하나로, 접속은 SASL_SSL + SCRAM + ACL (2026-11-09)

`공통` · **정함** — AI 쪽이 환경마다 계정을 따로 쓰는지, dev·test를 한 대로 겸하는지는 정하지 않음 ([ai-server/README.md](ai-server/README.md) 「정하지 않은 것」)
- **결정** — 토픽 `{env}.<토픽>`, 계정 `{env}.<서비스>`, 컨슈머 그룹 `{env}.<서비스>.<용도>`를 `KAFKA_ENV` 하나로 만든다. SASL_SSL + SCRAM-SHA-512, ACL은 기본 거부·이름 하나씩(LITERAL), 서비스 계정에 `CREATE` 없음, 토픽 자동 생성 끔.
- **버린 대안** — 이벤트 본문의 환경 필드로 거르기 — 토픽이 다르면 애초에 받지 않는다 / 접두어(PREFIXED) ACL — 나중에 같은 접두어로 생긴 토픽에 권한이 따라간다
- **이유** — dev·test가 클러스터 하나를 같이 쓰고 prod는 따로다 ([kafka.md](kafka.md) 「이름」 「ACL」)
- **영향** — kafka.md

### D-05 기록 카드 PR을 셋으로 나눈다 (2026-11-12)

`AI` · **정함** — 세 PR 모두 11-20까지 test·prod에 배포됐다
- **결정** — 9-1~9-2(Kafka 없음, 음악은 대기) → 아웃박스 + CDC 발행 → 완료 구독 + inbox. AI 서버는 요청을 받는 쪽 inbox를 따로 둔다.
- **버린 대안** — 한 PR에 발행까지 넣기 — 발행 안정성 설계가 커서 따로 뺐다
- **이유** — 카드 생성을 먼저 내고 발행 신뢰성은 독립 단계로 다룬다
- **영향** — api-spec 9-5, [outbox-inbox.md](outbox-inbox.md)

### D-06 이벤트는 아웃박스 + CDC로 보낸다 (2026-11-12, 도구·형식 11-16~17)

`공통` · **정함**
- **결정** — 업무 트랜잭션 안에서 `outbox_events`에 INSERT하고, Debezium Postgres 커넥터 + Outbox Event Router가 WAL에서 읽어 발행한다. 이벤트 ID는 UUIDv7 → 헤더 `id`, key·value는 `StringConverter`, 발행 상태 컬럼 없음, 앱에는 Kafka 프로듀서가 없다.
- **버린 대안** — 커밋 전 직접 발행 — 롤백된 카드의 음악이 만들어진다 / 커밋 뒤 직접 발행 — 발행이 실패하면 음악이 `GENERATING`에 영영 남는다 / JSON 변환기 — key에 따옴표가 붙고 value가 한 번 더 감싸지며, 펼치기 설정은 `null` 필드를 뺀다
- **이유** — [outbox-inbox.md](outbox-inbox.md) 「왜 쓰는가」, [cdc.md](cdc.md) 「Debezium 커넥터 설정」
- **영향** — outbox-inbox.md, cdc.md, kafka.md, erd `outbox_events`

### D-07 카드·사진 ID는 UUIDv7, 음악 key는 AI가 정한다 (2026-11-13)

`AI` · **정함** — D-02를 바꾼다
- **결정** — `record_card_id`·`record_card_image_id`는 9-1이 UUIDv7로 정한다. 음악 S3 key는 AI 서버가 정해 완료 이벤트로 돌려주고, 서버는 받은 key를 그대로 저장한다. 상태 이름은 `GENERATING`·`COMPLETED`·`FAILED`.
- **버린 대안** — bigint IDENTITY — INSERT 뒤에야 생겨 9-1이 사진 key를 만들 수 없다 / 서버가 key를 정하기(D-02) — AI 쪽 경로 규칙과 갈린다 / `PENDING`·`READY`·`FAILED`
- **이유** — 저장 전에 ID가 필요하고, AI가 같은 ID로 저장해 돌려주며, S3·Redis·DB·Kafka·AI DB가 한 값을 든다
- **영향** — api-spec 8·9장, erd, feature-spec

### D-08 MSA 문서는 docs/msa, 설정은 infra/kafka (2026-11-16)

`문서` · **정함** — D-60이 넓힌다
- **결정** — docs/msa에 README·kafka·cdc·outbox-inbox를 두고 커넥터·토픽 설정은 `infra/kafka/`에 둔다. 파일 이름은 도구보다 개념으로(cdc). api-spec 9-5에는 이벤트 계약만 남긴다.
- **버린 대안** — 운영 내용(보안·계정·ACL·환경 변수)을 api-spec 9-5에 두기 — 계약과 운영이 섞인다
- **이유** — 규칙은 docs, 설정은 infra(ELK 때 방식)
- **영향** — docs/msa 4개 파일, api-spec 9-5

### D-09 AI 서버 inbox는 헤더 `id`로 거른다 (2026-11-16)

`AI` · **제안** — AI 쪽 확인 전
- **결정** — 요청 이벤트의 중복은 Kafka 헤더 `id`(= `outbox_event_id`, UUIDv7)로 거른다. 본문은 바꾸지 않는다.
- **버린 대안** — 카드 ID(Kafka key)로 거르기 — 한 카드가 작성·수정·다시 만들기마다 정당하게 여러 번 보낸다
- **이유** — 아웃박스 행마다 하나이고 CDC가 다시 보내도 같다 (api-spec 9-5 요청 이벤트 「아직 정하지 않은 것」 4)
- **영향** — api-spec 9-5, [outbox-inbox.md](outbox-inbox.md) 「지금 있는 이벤트」

### D-10 test는 Kafka·Postgres·Connect를 한 EC2에 둔다 (2026-11-17)

`플랫폼` · **정함**
- **결정** — test의 Kafka·DB EC2를 t3.medium으로 키우고 Connect를 같은 EC2에 둔다(DB에 `localhost`로 붙는다). prod는 RDS와 별도 Kafka EC2(Connect 같이)로 독립이다. 새 구성은 test에서 먼저 끝내고 prod로 간다.
- **버린 대안** — t3.small에 Connect 올리기 — 여유 메모리가 모자랐다
- **이유** — DB를 EC2 밖에 열지 않는다. 대가로 WAL과 Kafka 로그가 한 디스크를 써 WAL이 차면 Kafka도 멈춘다 ([cdc.md](cdc.md) 「환경」)
- **영향** — cdc.md, kafka.md 「클러스터와 환경」, [platform.md](platform.md) 「test 환경」

### D-11 완료 이벤트 — inbox 키와 `GENERATING` 규칙 (2026-11-19)

`AI` · **바뀜 → D-12** (inbox 키) — D-15·D-16이 되돌리기·반영 규칙을 좁혔다
- **결정** — 완료 inbox 키는 본문 `inference_id`(헤더 `id`가 생기면 그쪽). AI는 안에서 재시도하고 요청마다 최종 결과를 한 번 보낸다. 메인은 보내기 전에 대상 음악을 `GENERATING`으로 되돌리고, 받으면 `GENERATING`인 것만 바꾼다.
- **버린 대안** — 수정·다시 만들기용 토픽을 따로 두거나 AI가 고쳐 다시 보내기 — 미뤘다 (api-spec 9-5 「나중에 다시 볼 것」)
- **이유** — AI는 생성·수정·다시 만들기를 구분하지 못하고 같은 경로에 덮어쓴다
- **영향** — api-spec 9-5, [outbox-inbox.md](outbox-inbox.md) 「받는 쪽 — Inbox」

### D-12 헤더 `id` 없는 완료는 건너뛰고, 잠깐 생긴 DB 문제만 다시 받는다 (2026-11-20)

`메인` · **정함** — D-11의 키를 바꾼다. 건너뛰는 쪽은 D-51이 좁힌다
- **결정** — inbox 키는 헤더 `id`(= `inference_id`)이고, 없으면 ERROR를 남기고 건너뛴다. 원인 SQLState `08`·`40`·`57`·`55P03`과 `SQLTransientException`·`SQLRecoverableException`만 다시 받고 나머지 예외는 건너뛴다(`KafkaConsumerConfig`). test·prod 배포(prod 1.3.1).
- **버린 대안** — 모든 예외를 다시 받기 — 파티션이 하나라 처리할 수 없는 메시지 하나가 뒤를 모두 막는다
- **이유** — #134 리뷰 ([outbox-inbox.md](outbox-inbox.md) 「처리할 수 없는 메시지」)
- **영향** — outbox-inbox.md, api-spec 9-5

### D-13 로컬은 가짜 AI 완료로 (2026-11-22)

`메인` · **정함**
- **결정** — `@Primary` `LocalAiMusicAdapter`가 아웃박스 어댑터를 감싸고, 커밋 5초 뒤 9-5 모양 완료 메시지를 Spring 이벤트로 같은 Mapper·usecase에 넣는다. `local` 프로필 + `KAFKA_CONSUMER_ENABLED=false` + `RECORD_CARD_MUSIC_LOCAL_AI_ENABLED`가 모두 맞을 때만 켜지고, E2E는 끈다.
- **버린 대안** — 프로필 하나로만 켜기 — 프로필을 잘못 넣은 배포 서버에서 켜질 수 있다
- **이유** — 로컬에는 Debezium·Kafka·AI가 없어 음악이 `GENERATING`에 멈춘다. 포트·Handler·도메인은 그대로다
- **영향** — [outbox-inbox.md](outbox-inbox.md) 「로컬 가짜 AI (음악 완료)」

### D-14 음악은 기록 카드와 다른 버킷 (2026-11-23)

`AI` · **정함** — 버킷 이름은 정하지 않음 (api-spec 9-5 「아직 정하지 않은 것」)
- **결정** — 음악은 별도 버킷에서 읽는다(`storage.s3.music-bucket=${S3_MUSIC_BUCKET}`, `S3Properties.musicBucket`). 기본값이 없어 없으면 기동하지 않는다 — 배포 전에 test·deploy Parameter Store에 넣고 앱 역할에 읽기 권한을 준다.
- **버린 대안** — 기록 카드 버킷에 함께 두기 — AI가 정한 key가 기록 카드 key 규칙(`record-cards/...`) 밖이다
- **이유** — 음악은 AI가 올리고 앱은 보기 URL만 서명한다
- **영향** — [platform.md](platform.md) 「S3」, [ai-server/README.md](ai-server/README.md) 「정하지 않은 것」

### D-15 기본곡은 어떤 결과든 따르고, 사진 음악은 `GENERATING`일 때만 (2026-11-23)

`AI` · **정함** — D-11을 좁힌다
- **결정** — 기본곡은 수정 때 되돌리지 않고, 끝난 곡에도 온 결과(성공·실패)를 따른다. 사진 음악은 `GENERATING`일 때만 바꾼다. 겹친 요청을 가를 `request_id`는 두지 않는다.
- **버린 대안** — 기본곡도 `GENERATING`일 때만 바꾸기 — 다시 만든 기본곡의 새 key(활동유형이 바뀌면 경로도 바뀐다)를 잃는다 / `request_id` — 늦은 결과가 덮을 수 있는 한계를 문서에 남기고 받아들였다
- **이유** — AI는 요청마다 기본곡을 다시 만든다 (api-spec 9-5 요청 이벤트 「아직 정하지 않은 것」 3)
- **영향** — api-spec 9-3·9-5, [outbox-inbox.md](outbox-inbox.md) 「받는 쪽 — Inbox」

### D-16 음악 다시 만들기(9-6)는 `FAILED`인 음악만 (2026-11-23)

`AI` · **정함** — D-11을 좁힌다
- **결정** — 9-6은 `FAILED`인 음악만 `GENERATING`으로 되돌려 다시 요청한다. 기본곡도 `FAILED`일 때만.
- **버린 대안** — 전부 되돌려 다시 요청하기 — 끝난 곡까지 되돌렸다 (669f07f8, #140에서 고침)
- **이유** — 끝난 곡을 다시 만들거나 가리면 안 된다
- **영향** — api-spec 9-5·9-6, `RetryRecordCardMusicHandler`

## 러닝 세션 분리 (11-24)

같은 날 논의 순서대로 적는다. 첫 검토의 권고(D-18~D-21)와 둘째 검토의 일부(D-33·D-38·D-39·D-40)는 뒤 결정으로 바뀌었다.

### D-17 4단계 계획 (2026-11-24)

`공통` · **정함** — 단계마다 좁혀졌다: 2단계 D-22, 3단계 D-43, 4단계 D-23·D-48
- **결정** — (1) k6로 러닝 세션을 나눌 이유를 찾는다 → (2) 모놀리스 구조로 바꾼다 → (3) 나누고 saga 등을 적용한다 → (4) EKS에 올린다. (2)는 D-20이 모듈러 모놀리스로 읽었고 D-22가 '모놀리스 안에 MSA 모양 새 모듈'로 바꿨다. 측정 → 경계 → 분리 → 플랫폼 순서를 지킨다.
- **이유** — 처음 세운 계획
- **영향** — [running-session-server/README.md](running-session-server/README.md) 「진행 단계」

### D-18 DB 없는 실시간 가장자리만 떼기 (2026-11-24)

`세션` · **바뀜 → D-24** — 첫 검토의 권고
- **결정** — WS·Redis·끝내기의 봉인 절반·S3만 떼고, 방·참가자·기록과 끝내기의 DB 절반은 메인에 둔다. 시작·끝내기는 동기 멱등 호출이고 ack는 메인의 2xx 뒤다. saga는 없다.
- **이유** — 두 번째 DB와 CDC를 피한다

### D-19 EKS는 미루고 두 함대부터 (2026-11-24)

`플랫폼` · **바뀜 → D-23** — 첫 검토의 권고. 남은 것: 비교군 B0와 AG6, 역할 플래그
- **결정** — EKS를 미루거나 접는다. 같은 jar를 두 번째 ASG + ALB 경로 규칙(`/api/v1/ws/running*`)으로 나누거나 ECS를 먼저 쓴다. EKS는 기간을 정한 학습으로만.
- **이유** — 측정된 문제 중 EKS가 고치는 것이 없다. IaC·drain·연결 게이지·추적이 없고 prod 빌드가 테스트를 건너뛴다

### D-20 모듈러 모놀리스가 끝 상태, 분리는 조건부 (2026-11-24)

`세션` · **바뀜 → D-22** (2단계 해석), **D-45** (분리 이유) — 첫 검토의 권고
- **결정** — 2단계 '모놀리스 전환'을 끝 상태인 모듈러 모놀리스로 읽는다. 모놀리스 안 칸막이 뒤에도 REST 저하·확장 불일치·배포 결합이 남을 때만 나누고, 아니면 모듈러 모놀리스에서 멈춘다(가부 관문).
- **이유** — run 14는 고칠 수 있는 결함이었다

### D-21 ALB 가중치 카나리와 '100% 2주 뒤 삭제' (2026-11-24)

`세션` · **바뀜 → D-36** (라우팅), **D-37** (지울 기준) — 첫 검토의 권고
- **결정** — 새 엔진을 ALB 가중치 5→25→50→100%로 올리고, 100%에서 2주 지나면 모놀리스 안 경로를 지운다.
- **이유** — 첫 검토의 단계안

### D-22 지우지 않고 모놀리스 안에 새로 만든다 (2026-11-24)

`세션` · **정함** — D-20의 2단계 해석을 바꾼다
- **결정** — 기존 코드는 지우지 않고 기능을 모두 유지한다. 러닝 세션을 MSA 모양으로 모놀리스 안에 새로 만들고 → 검증하고 → 별도 저장소로 옮기고 → 새것이 옛 코드 없이 돌면 옛 코드를 지운다(legacy/v1_1 보존과 같은 결).
- **버린 대안** — 지우고 새로 쓰기 — 되돌릴 곳이 없다 / 모듈러 모놀리스를 끝 상태로(D-20) — 계획은 별도 서버까지 간다
- **이유** — 지우기를 맨 뒤에 두고 되돌릴 창을 드러낸다. 새 엔진이 증명될 때까지 검증된 ALB·ASG·E2E·대시보드를 그대로 쓴다
- **영향** — [running-session-server/migration.md](running-session-server/migration.md) 「방식」 「단계 M0–M9」

### D-23 EKS는 목표다 (2026-11-24)

`플랫폼` · **정함** — D-19를 바꾼다. D-48이 앱 워크로드로 좁혔다
- **결정** — AWS에서 Kubernetes를 최대한 쓰는 최적 설계를 목표로 한다. 배우는 것도 목표에 든다.
- **버린 대안** — EKS 미루기·두 함대 먼저(D-19) — 목표와 맞지 않는다 / ECS — 목표가 Kubernetes다
- **이유** — 목표 — Kubernetes를 끝까지 쓰는 설계와 배우기
- **영향** — [eks.md](eks.md)

### D-24 러닝 기록의 정본은 러닝 DB, 메인은 사본 (2026-11-24)

`공통` · **정함** — D-18을 바꾼다
- **결정** — 러닝 세션 서버가 자기 Postgres 러닝 DB(두 번째 RDS)를 갖고 러닝 기록의 단일 정본으로 둔다. 메인은 사본을 갖고, 러닝 DB 아웃박스 → Debezium(두 번째 커넥터·슬롯) → 결과 토픽 → 메인 inbox로 받는다.
- **버린 대안** — DB 없는 가장자리(D-18) — 기록 + 아웃박스를 한 트랜잭션으로 커밋한 뒤 ack하면 메인 없이도 기록이 남고, 시작·끝내기의 메인 동기 호출은 장애 격리를 깬다 / 기록은 메인에 두고 RDS 하나 — 끝내기가 메인에 동기로 기댄다
- **이유** — `RUNNING_FINISHED` 불변식이 로컬 트랜잭션 하나로 지켜지고, 시작·끝내기 폭주가 세션 자기 풀에 걸린다 ([running-session-server/consistency.md](running-session-server/consistency.md) 「불변식 I1–I7」, [running-session-server/rationale.md](running-session-server/rationale.md) 「G3 장애 격리」)
- **영향** — [running-session-server/architecture.md](running-session-server/architecture.md) 「데이터 주인」 「메인 사본과 반영」, [platform.md](platform.md) 「RDS」 「러닝 DB CDC」, erd(스키마를 정할 때)

### D-25 새 k6 시험으로 반증하고, 옛 결과는 기준선으로만 (2026-11-24)

`세션` · **정함**
- **결정** — 분리 근거는 새 k6 시험으로 찾고 확인한다. 시험은 근거를 깰 수 있게 짠다. load_test/RESULTS.md의 옛 숫자는 run id를 달아 기준선으로만 쓴다. 비교군(A′ · B0 등)은 D-57(제안)에 둔다.
- **버린 대안** — 옛 결과를 분리 근거로 쓰기 — 10-23 뒤 커밋을 재지 않았고, '러닝은 수집, 메인은 표시'를 넘는 근거를 보여 주지 못한다
- **이유** — 새 시험으로 찾기로 했다 ([running-session-server/verification.md](running-session-server/verification.md) 「원칙」)
- **영향** — verification.md, [load_test/msa/PREREG.md](../../load_test/msa/PREREG.md)

### D-26 경계는 생애 단계로 나눈다 (2026-11-24)

`공통` · **정함** — 넘기는 방식은 정하지 않음 ([running-session-server/architecture.md](running-session-server/architecture.md) 「넘기기」)
- **결정** — 명단이 정해질 때까지(매칭 모집 마감·초대 방 시작 직전·혼자 달리기 생성) 모집은 메인이, 그 뒤 러닝은 세션이 맡는다. 메인은 읽기 모델을 갖는다.
- **버린 대안** — 동사로 나누기('러닝은 수집, 메인은 표시') — 매칭과 러닝이 한 애그리거트라 그 선은 saga로도 이을 수 없다 ([running-session-server/migration.md](running-session-server/migration.md) 「지금 얽힌 곳 (11-24 기준)」)
- **이유** — 명단이 정해진 뒤에는 줄어들기만 해 단계마다 주인이 하나다. 수집·표시 구분은 데이터 주인으로만 남는다 ([running-session-server/rationale.md](running-session-server/rationale.md) 「받쳐 주는 근거 G5–G8」)
- **영향** — architecture 「경계 — 생애 단계」

### D-27 방 번호와 '한 사람 활성 신청 하나'는 메인이 단독으로 (2026-11-24)

`메인` · **제안**
- **결정** — 메인이 `runningRoomId`를 혼자 매기고 사용자마다 활성 신청 하나를 지킨다. 넘긴 자리는 세션 이벤트나 이탈 응답 없이 혼자 풀지 않는다. 혼자 달리기 생성도 메인에 남는다.
- **버린 대안** — 세션이 혼자 달리기 방을 만들기 — 하나뿐인 공개 정수 ID 공간에서 번호가 겹치거나, 예약 saga와 청소기가 생긴다
- **이유** — 이중 예약이 없다. 대가는 CDC 지연 동안 사용자가 활성으로 보이는 것이다 (D-31)
- **영향** — architecture 「메인 쪽 불변식」, consistency 「불변식 I1–I7」(I4)

### D-28 결과 이벤트 셋과 사용자 생애 이벤트 (2026-11-24)

`공통` · **제안** — 토픽·계정·그룹 이름과 파티션은 정하지 않음 ([platform.md](platform.md) 「새 이름」)
- **결정** — 세션 → 메인은 참가자 완주·참가자 이탈·세션 닫힘 셋으로 나눈다. 메인 → 세션은 방 확정(넘기기 후보)·사용자 삭제·프로필 변경을 보낸다. key는 방 ID, 구간 기록은 싣지 않으며, 계약은 더하기만 하고 버전을 붙인다.
- **버린 대안** — 결과 이벤트 하나 — 노쇼·확정 뒤 이탈과 세션 닫힘을 담지 못한다
- **이유** — [running-session-server/architecture.md](running-session-server/architecture.md) 「이벤트 (후보)」 — 모양이 정해지면 api-spec 14장으로 옮긴다
- **영향** — architecture 「이벤트 (후보)」, platform 「새 이름」

### D-29 예약 작업은 종류별로 주인을 나눈다 (2026-11-24)

`공통` · **제안**
- **결정** — 러닝 시작 · 강제 종료는 세션, 모집 쪽은 메인 — 서비스마다 자기 `scheduled_jobs`와 채널을 둔다. 종류마다의 주인은 [running-session-server/architecture.md](running-session-server/architecture.md) 「데이터 주인」.
- **버린 대안** — 테이블 하나·채널(`schedule:job`) 하나를 같이 쓰기 — 자기 실행기가 없는 종류를 받으면 `RunScheduledJobHandler`가 `IllegalStateException`을 던진다
- **이유** — 러닝 시작·강제 종료는 러닝을 가진 쪽이 맡는다
- **영향** — architecture 「데이터 주인」, platform 「Redis」

### D-30 JWT는 ES256 — 메인이 서명, 세션은 검증만 (2026-11-24)

`공통` · **제안** — 폐기된 토큰을 세션이 아는 방법은 정하지 않음 (architecture 「인증」)
- **결정** — 메인이 ES256으로 서명하고 세션은 공개 키로 로컬에서 검증한다. 이미 나간 토큰을 위해 두 키를 함께 받는 기간을 둔다. 그 전까지는 끝날 날짜를 정한 임시안(HS256 비밀값으로 검증만 + 블랙리스트 읽기 전용)을 쓴다.
- **버린 대안** — HS256 비밀값 공유(지금 `JwtTokenAdapter`·`JwtDecoderConfig`) — 세션이 토큰을 만들 수 있다 / 실행 중 메인 JWKS 호출 — 핸드셰이크가 메인에 동기로 기댄다
- **이유** — 위치·비밀값 가두기(G7)는 이것 뒤에만 근거가 된다 ([running-session-server/rationale.md](running-session-server/rationale.md) 「받쳐 주는 근거 G5–G8」)
- **영향** — [running-session-server/architecture.md](running-session-server/architecture.md) 「인증」

### D-31 끝낸 뒤 메인이 반영하기 전까지의 창을 받아들인다 (2026-11-24)

`메인` · **제안** — D-58은 7-1·기록 카드가 늦게 보이는 것만 정했다
- **결정** — 메인이 결과를 반영하기 전까지 그 사용자를 활성으로 본다 — 신청·혼자 달리기·초대 수락을 거절하고 `/users/me/status`는 RUNNING이다. 막는 장치는 DLT·건너뜀 알람·대조 당겨 오기, 재는 것은 다시 신청할 수 있기까지의 시간과 CDC 지연이다.
- **버린 대안** — 다시 시도하라는 새 오류 코드 — API 변경이라 승인과 최소 버전 상향이 필요하다
- **이유** — 메인이 자리를 혼자 풀지 않는 대가다 (D-27)
- **영향** — [running-session-server/consistency.md](running-session-server/consistency.md) 「종료 뒤 최종 일관 창」, verification SC6

### D-32 날씨 조회를 끝내기 경로에서 뺀다 (2026-11-24)

`공통` · **바뀜 → D-63** (11-25) — 끝낼 때 잠금 밖에서 조회하기로 정했다. 아래 (가)(나)는 버렸다
- **결정** — 지금 끝내기 안에서 잠금을 쥔 채 하는 날씨 조회(`RunningFinisher` → `LoadWeatherPort`, Open-Meteo)를 끝내기 경로 밖으로 뺀다. 후보는 둘이다 — (가) 세션이 시작 때 미리 받는다: 위치가 세션 안에 머문다. (나) 메인이 결과를 반영한 뒤 채운다: 메인이 시작 좌표를 받아야 하고, 날씨가 비어 있는 동안 9-2 카드가 만들어질 수 있다.
- **버린 대안** — 끝내기 트랜잭션 안에 두기 — 잠금을 쥔 채 외부 호출을 기다린다 (`RunningFinisher`의 TODO)
- **이유** — 기록의 날씨는 9-5 음악 요청 재료로만 쓰인다
- **영향** — architecture 「메인 사본과 반영」, api-spec 5-D 부수효과(시작 좌표를 누가 보내는가)·9-5 `running_summary.weather_code`, feature-spec 날씨, rationale G7 — [running-session-server/migration.md](running-session-server/migration.md) 「단계마다 고칠 문서」

### D-33 k6 첫 설계(v1) — 성능 중심 (2026-11-24)

`세션` · **바뀜 → D-57** — 남은 것: 실행 규칙·하네스 구조·판정 모양 ([load_test/msa/PREREG.md](../../load_test/msa/PREREG.md), [load_test/msa/README.md](../../load_test/msa/README.md))
- **결정** — 후보 근거를 모두 재는 성능 중심 묶음 — 같은 비용의 비교군 A·A′·A′c·A′R2·B0·B1·B2, 시나리오 15개, 대조군.
- **이유** — 모든 후보 근거를 시험한다

### D-34 끝내기 결함은 옛 엔진에서 먼저 고친다 (F1–F9) (2026-11-24)

`세션` · **F1 바뀜 → D-63** (11-25) — 끝내기 구조(F1)와 날씨는 새 엔진에서만 고친다. F2–F9를 모놀리스에서 먼저 할지는 제안 그대로다
- **결정** — run 14의 원인(잠금과 커넥션을 쥔 채 S3 업로드·트랙 분석, 같은 방 행 잠금 대기 — [load_test/RESULTS.md](../../load_test/RESULTS.md) 「정정 (11-24)」)과, 운영에서만 드러나는 같은 트랜잭션 안 날씨 조회(최대 2초)를 옛 엔진에서 먼저 고치고 다시 잰다. 고친 묶음 F1–F9가 비교군 A′다.
- **버린 대안** — 나눠서 해결하기 — 나눠도 같은 `RunningFinisher`가 같은 폭주에서 실패한다
- **이유** — [running-session-server/rationale.md](running-session-server/rationale.md) 「G4 끝내기 폭주는 고칠 결함」
- **영향** — [running-session-server/migration.md](running-session-server/migration.md) 「선행 수정 F1–F9」, verification 「비교군」

### D-35 새 엔진은 Gradle 서브프로젝트 (2026-11-24)

`세션` · **제안**
- **결정** — 서브프로젝트 `running-session`, base package `com.runiverse.running_session`. 모놀리스는 `runiverse.running-session.enabled` 뒤에서 `@Import`로 켠다. 별도 프로세스 호스트는 나중에 더한다(이름은 D-61).
- **버린 대안** — 그냥 폴더 — 아무것도 막지 못해 import가 샌다 / 멀티 프로젝트를 뒤로 미루기 — 같은 이유
- **이유** — 모듈이 모놀리스를 import하면 컴파일이 실패하고, 엔티티가 메인 persistence unit 밖에 있다
- **영향** — migration 「모듈 모양」

### D-36 엔진은 방마다 명단 확정 때 정하고 바꾸지 않는다 (2026-11-24)

`세션` · **제안** — D-21의 가중치 카나리를 바꾼다
- **결정** — 명단이 정해질 때 방마다 엔진을 골라 방에 적고 바꾸지 않는다. 지금 WS 주소(`/api/v1/ws/running`) 앞의 얇은 라우터가 `runningRoomId`로 그 엔진에 보낸다.
- **버린 대안** — ALB 가중치 대상 그룹(D-21) — 연결 단위라 방이 둘로 갈려 진행률·콤보를 놓치고 6-1이 반쪽이 된다
- **이유** — 방이 온전하고 공개 주소가 바뀌지 않는다
- **영향** — migration 「라우팅」 「진행 중 방과 되돌리기」

### D-37 지울 기준 A · B (2026-11-24)

`세션` · **제안** — D-21의 '100% 2주 뒤 삭제'를 바꾼다
- **결정** — 옛 엔진(A)과 모놀리스 안 호스트(B)를 따로 지운다. 기간만이 아니라 진행 중인 옛 방 0·대조 누락 0·두 엔진 E2E 통과·api-spec 차이 없음·되돌리기 연습 같은 증거로 정한다.
- **버린 대안** — 100%에서 2주 뒤(D-21) — 기간만 보고 진행 중 방·대조·되돌리기를 보지 않는다
- **이유** — [running-session-server/migration.md](running-session-server/migration.md) 「지울 기준 A · B」
- **영향** — migration M8·M9

### D-38 러닝 중 탈퇴는 503으로 거절 (2026-11-24)

`공통` · **바뀜 → D-43** — 503은 saga의 보상 응답으로 남는다
- **결정** — 새 엔진에서 러닝 중인 사용자의 탈퇴는 `ACCOUNT_DELETION_UNAVAILABLE`(503)로 거절한다.
- **이유** — 가장 단순한 선택

### D-39 모노레포에 배포물 둘 (2026-11-24)

`세션` · **바뀜 → D-47**
- **결정** — 한 저장소에 배포물 둘 — 계약을 한 번에 바꾸고 E2E·문서가 하나다.
- **이유** — 주인·언어·권한이 다르거나 CI가 괴로울 때만 저장소를 나눈다

### D-40 EKS 종합안의 데이터 배치 (2026-11-24)

`플랫폼` · **바뀜 → D-48, D-49, D-50** — 배치와 무관하게 남는 것: Debezium 3.7.1, 러닝 DB용 두 번째 커넥터·슬롯, acks=all + 멱등 프로듀서, Connect를 옮길 때 같은 슬롯 이름 + `snapshot.mode=no_data`로 넘기기
- **결정** — 서비스마다 ElastiCache Valkey, 클러스터 안 Strimzi Kafka + KafkaConnect, AI DB는 RDS MySQL, 상태용 노드 풀. 그때 계산한 비용은 최소 ≈$987/월, 전체 ≈$1,725/월, 클러스터 하나로 줄인 안 ≈$517/월이다 — 이 배치 기준이라 지금 예산이 아니다.
- **이유** — Kubernetes를 끝까지 쓰는 설계
- **영향** — 비용은 [eks.md](eks.md) 「비용」에서 다시 계산한다

### D-41 EKS 앱 쪽 설계 (2026-11-24)

`플랫폼` · **제안** — 클러스터 수, Karpenter와 Auto Mode는 정하지 않음 ([eks.md](eks.md) 「정하지 않은 것」)
- **결정** — 표준 모드 Kubernetes 1.36, 업그레이드 정책 STANDARD. 클러스터 둘(test 먼저). 시스템 노드는 Graviton 관리형 노드 그룹, 앱 노드는 직접 운영하는 Karpenter. 워크로드는 main-api·session-ws·session-worker·ai-server. AWS LBC Ingress, KEDA(슬롯 시간 cron + WS 연결 게이지), Argo CD + Argo Rollouts 직접 운영과 슬롯 시간 동기화 금지 창.
- **버린 대안** — 1.37 — KEDA 2.21과 Karpenter LTS가 1.36까지다 / 연장 지원 — 업그레이드를 놓쳐도 요금만 늘고 드러나지 않는다 / EKS 관리형 Argo CD — 요금이 따로 붙고 배우는 것이 적다 / 지금 Gateway API — Argo Rollouts의 ALB 트래픽 나누기가 Ingress를 대상으로 한다(test에서 나중에 시험) / 방마다 Kubernetes Job — 18:00:00에 수백 방을 초 단위로 시작해야 한다. Auto Mode는 종합안에서 버렸으나(노드 수명 21일 강제 — 슬롯 중에 소켓을 든 노드가 비워질 수 있다) 지금은 정하지 않음이다
- **이유** — [eks.md](eks.md)
- **영향** — eks.md

### D-42 계획 종료는 닫기 코드 4500을 다시 쓴다 (2026-11-24)

`세션` · **제안**
- **결정** — 인스턴스·파드를 내릴 때 소켓을 `4500`으로 닫는다. 앱은 이미 이 코드를 재연결로 처리한다(api-spec 5).
- **버린 대안** — 전용 닫기 코드 — iOS·안드로이드 출시와 최소 버전 상향이 필요하다
- **이유** — 앱 출시 없이 지금 계약으로 된다
- **영향** — [running-session-server/runtime.md](running-session-server/runtime.md), eks 「계획 종료」, migration 「선행 수정 F1–F9」

### D-43 러닝 중 계정 탈퇴는 saga로 (2026-11-24)

`공통` · **정함** — D-38을 바꾼다. 단계 모양은 D-44(제안)
- **결정** — 이 설계에서 saga는 러닝 중 계정 탈퇴 하나다. 끝내기·넘기기·혼자 달리기·결과 조회는 saga가 아니다.
- **버린 대안** — 러닝 중 탈퇴를 503으로 거절하기(D-38) — saga로 정했다. 503은 보상 응답으로 남는다
- **이유** — 팀 결정 ([running-session-server/consistency.md](running-session-server/consistency.md) 「saga가 아닌 것」)
- **영향** — consistency 「계정 탈퇴 saga」, api-spec 12-4(계약 그대로)

### D-44 탈퇴 saga의 단계 (2026-11-24)

`공통` · **제안** — 정산이 러닝에 무엇을 하는지(강제 종료 vs 이탈), 지금 탈퇴 경로와 맞추는 법, 메시지·시간 제한·멱등 키는 정하지 않음 (consistency 「정하지 않은 것」)
- **결정** — 메인이 지휘한다: 사용자를 DELETING으로 표시 → 넘기기 전 메인 상태를 정리 → 세션에 동기 정산 요청(되돌리지 않는 기점) → 메인이 삭제하고 사용자 삭제 이벤트를 아웃박스에 쓴다 → 세션이 모든 인스턴스의 소켓을 닫는다. 기점 전에 실패하면 DELETING을 풀고 지금의 503 `ACCOUNT_DELETION_UNAVAILABLE`로 답하고, 기점 뒤에는 앞으로 재시도한다.
- **버린 대안** — 세션이 혼자 달리기 방을 만들기 — 예약 saga가 늘어난다
- **이유** — 넘길 때 몸무게·프로필을 스냅샷으로 넘겨 정산에 사용자 행이 필요 없다
- **영향** — consistency 「계정 탈퇴 saga」, architecture 「동기 호출 허용 목록」, verification SC7

### D-45 러닝 세션을 나누는 이유는 안정성 (2026-11-24)

`공통` · **정함** — D-20의 분리 조건 규칙을 바꾼다(가부 관문은 검증 관문이 된다)
- **결정** — 러닝 세션은 더 안정적인 서비스를 위해 따로 개발하고, 메인은 콘텐츠와 러닝 밖 기능을 개발한다. 메인 배포·장애가 진행 중 러닝을 끊지 않게 하는 것이 목표다 — 앱이라 밤까지 배포를 미룰 수 없고, 매칭은 끊겨도 견디지만 러닝은 콤보 때문에 중간에 끊기면 크게 꼬인다. k6는 러닝 연속성을 잰다.
- **버린 대안** — 성능 때문에 나누기 — 1,000명까지 여유가 있었다 (load_test/RESULTS run 13·53) / 조건부 분리(D-20) — 나누기로 정했다
- **이유** — [running-session-server/rationale.md](running-session-server/rationale.md) 「분리 이유 — 운영에서 확인한 것」 「G1 배포 결합」
- **영향** — rationale.md, verification.md

### D-46 결과 조회(6-1·6-2)는 메인이 응답 (2026-11-24)

`메인` · **바뀜 → D-58**
- **결정** — 끝낸 직후 결과를 메인이 사본으로 응답해 복제가 맞는지 확인한다. 같은 날 앞선 제안(세션이 응답)을 바꿨다.
- **버린 대안** — (가) 메인이 기다렸다가 세션에 동기로 넘기기 — 메인 → 세션 동기 의존 / (나) 앱이 PENDING을 받고 다시 묻기 — API 변경, iOS·안드로이드 최소 버전 상향. 함께 본 안: (다) 앱 재시도 + CDC 지연이 화면 전환보다 짧음을 증명하기
- **이유** — 복제가 맞는지 메인에서 바로 확인하려 했다

### D-47 러닝 세션 서버는 별도 저장소 (2026-11-24)

`세션` · **정함** — D-39를 바꾼다. 옮기는 때는 D-62(제안)
- **결정** — 러닝 세션 서버를 다른 저장소로 낸다. 받아들인 비용: 버전 붙은 이벤트 계약과 공개 픽스처·계약 테스트, 공유 코드(트랙 분석·폴리라인·JWT 검증)를 공개하거나 동등성 테스트 아래 복제, 계약 하나에 PR 둘, 문서·spec-check 나누기, 서비스마다 이미지·ECR·파라미터·CI, 테스트를 도는 prod 빌드.
- **버린 대안** — 모노레포에 배포물 둘(D-39) — MSA 분리가 너무 어려워진다
- **이유** — 팀 결정
- **영향** — migration 「새 저장소가 따르는 규칙」 「단계 M0–M9」, [running-session-server/README.md](running-session-server/README.md) 「저장소를 나눌 때」

### D-48 예산 절약 배치 — Kafka·Redis는 EC2, RDS 둘, EKS는 앱만 (2026-11-24)

`플랫폼` · **정함** — D-40을 바꾸고, D-23을 앱 워크로드로 좁힌다. EKS 비용은 다시 계산한다
- **결정** — Kafka·Redis는 EC2. RDS는 서비스 DB·러닝 DB 둘뿐. Redis는 서비스마다 EC2 한 대. EKS는 앱 워크로드만.
- **버린 대안** — EKS 종합안의 배치(D-40) — 1~3명 팀 예산에 맞지 않는다
- **이유** — 비용. 데이터·인프라는 이미 검증된 자리에 둔다
- **영향** — [platform.md](platform.md) 「배치 — 지금과 목표」, [eks.md](eks.md) 「비용」

### D-49 Redis는 EC2 + AOF, 서비스마다 한 대 (2026-11-24)

`플랫폼` · **정함** — D-01을 이어받고, D-40의 ElastiCache를 바꾼다
- **결정** — 세션 Redis와 메인 Redis를 따로 두고 둘 다 AOF `everysec`이다. `maxmemory-policy noeviction`은 제안이다(platform 「Redis」). 각 한 대라 단일 장애점인 것은 받아들인다. 어느 키·채널이 어디에 있는지는 [platform.md](platform.md) 「Redis」.
- **버린 대안** — ElastiCache — AOF가 없고, 내구성 모드는 클러스터 모드 + 해시 태그가 필요한데 트랙 Lua(`RunningTrackRedisAdapter`)가 해시 태그 없는 키 두 개를 써 CROSSSLOT이 난다 / 한 Redis를 키 주인 약속으로 같이 쓰기 — 실패 영역이 그대로 같다
- **이유** — D-01, [running-session-server/rationale.md](running-session-server/rationale.md) 「G2 Redis 연결 · 실패 영역 공유」
- **영향** — platform 「Redis」, verification SC4

### D-50 Kafka는 당분간 EC2 한 대 (2026-11-24)

`플랫폼` · **정함** — D-40의 Strimzi를 바꾼다. 브로커 셋(RF 3)·Strimzi·MSK 중 무엇으로 갈지는 정하지 않음 (나중에 다시)
- **결정** — 지금의 EC2 Kafka(노드 한 대)를 그대로 쓴다.
- **버린 대안** — 클러스터 안 Strimzi(D-40) — D-48이 뒤집었다 / 지금 MSK — 비용이 들고, Serverless는 IAM 인증뿐이라 SCRAM 클라이언트를 바꿔야 한다
- **이유** — Strimzi와 MSK는 고르지 않고 권고안(EC2 유지)을 따랐다. 아웃박스 덕에 Kafka는 러닝 경로 밖이다
- **영향** — [kafka.md](kafka.md) 「정하지 않은 것」, [platform.md](platform.md) 「정하지 않은 것」

### D-51 결과가 Kafka를 타기 전에 DLT·건너뜀 알람·대조 (2026-11-24)

`공통` · **정함** — D-12의 건너뛰기를 좁힌다. DLT 토픽(`{topic}.{group}.dlt`)인지 보관 테이블인지는 정하지 않음
- **결정** — 러닝 결과 이벤트를 받기 전(M2)에 처리하지 못한 메시지를 모으는 곳, 건너뜀·보관 메트릭과 알람, 러닝 DB와 메인 사본의 대조를 만든다. DLT를 토픽으로 정하면 앱이 DLT에 쓰는 것이 '앱은 Kafka로 직접 보내지 않는다'([outbox-inbox.md](outbox-inbox.md) 「보내는 쪽 — 아웃박스」)의 하나뿐인 예외가 된다.
- **버린 대안** — 지금처럼 ERROR를 남기고 건너뛰기 — 건너뛴 결과는 메인 사본에서 영영 빠진 기록이 된다
- **이유** — [running-session-server/rationale.md](running-session-server/rationale.md) 「반대 근거 AG1–AG6」(AG5)
- **영향** — [outbox-inbox.md](outbox-inbox.md) 「받는 쪽 — Inbox」 「정하지 않은 것」 1–2, api-spec 9-5 「아직 정하지 않은 것」, [README.md](README.md) 「통신 — 어디로 무엇이 지나가는가」, consistency 「대조」

### D-52 서비스 사이 동기 호출은 허용 목록에 있는 것만 (2026-11-24)

`공통` · **정함** — 목록 후보와 공통 규칙은 D-53(제안)
- **결정** — '서비스 사이는 Kafka로만'에 예외 하나를 둔다. [running-session-server/architecture.md](running-session-server/architecture.md) 「동기 호출 허용 목록」에 적힌 호출만 허용하고, 호출마다 이유·시간 제한·멱등 키·실패 때 동작을 적는다. 목록 밖 호출은 금지이고 `check_conventions.py`로 막는다.
- **버린 대안** — 동기 호출을 아예 막기 — 모르는 방 당겨 오기와 탈퇴 기점을 처리할 수 없다 / 자유로운 동기 호출 — 장애 격리(G3)를 깬다
- **이유** — 권고안을 받아들였다
- **영향** — [README.md](README.md) 「통신 — 어디로 무엇이 지나가는가」, architecture 「동기 호출 허용 목록」

### D-53 허용 목록 후보와 공통 규칙 (2026-11-24)

`공통` · **제안** — 확정 뒤 이탈 전달은 「확정 뒤 이탈」이 정하지 않음이라 후보로만 둔다
- **결정** — 후보 호출 여섯과 공통 규칙(내부 포트 · ALB 404 · 서비스 인증 · 시간 제한 · 잠금 쥐지 않기 · 18:00 경로 금지)은 [running-session-server/architecture.md](running-session-server/architecture.md) 「동기 호출 허용 목록」에 둔다.
- **버린 대안** — 결과 조회의 동기 대체 — D-58로 필요 없어졌다
- **이유** — [running-session-server/architecture.md](running-session-server/architecture.md) 「동기 호출 허용 목록」
- **영향** — architecture, migration 「선행 조건」(내부 포트)

### D-54 모듈 경계는 check_conventions.py를 넓혀 지킨다 (2026-11-24)

`공통` · **정함**
- **결정** — 모듈 사이 import, JPQL 문자열 안 엔티티 이름, 옛 엔진 목록을 `.claude/skills/spec-check/scripts/check_conventions.py`에서 검사한다(`check_legacy_isolation`과 같은 방식). CI에서 위반 0.
- **버린 대안** — ArchUnit — 새 테스트 의존성 / Spring Modulith — 최상위 패키지 단위 모듈이 레이어 우선 배치와 맞지 않는다
- **이유** — 새 의존성 없이 legacy 격리 검사와 같은 방식이다
- **영향** — migration 「경계 검사」

### D-55 추적은 OTel Java 에이전트를 붙인다 (2026-11-24)

`공통` · **정함**
- **결정** — 빌드를 바꾸지 않고 OTel Java 에이전트를 실행 때 붙인다. 아웃박스 두 곳의 traceparent는 나중에 더한다. Boot 4.1·Tomcat 11에서 동작은 test에서 확인한다.
- **버린 대안** — Micrometer Tracing + OTel 브리지(첫 검토) — 빌드·의존성 변경
- **이유** — 의존성을 바꾸지 않는다
- **영향** — [platform.md](platform.md) 「관측」

### D-56 러닝 DB 스키마는 Flyway (2026-11-24)

`세션` · **정함** — M1에 들일지 저장소를 나눌 때 들일지는 정하지 않음 (migration 「정하지 않은 것」)
- **결정** — 러닝 DB 스키마는 Flyway로 관리한다. 메인 운영 스키마는 지금처럼 손으로 쓴 SQL이다.
- **버린 대안** — 러닝 DB도 손으로 쓴 SQL — 새 저장소의 새 DB는 실행할 수 있는 스키마로 시작할 수 있다
- **이유** — 권고안을 받아들였다
- **영향** — platform 「RDS」, erd(러닝 DB 장, 임시)

### D-57 k6 2차 설계(v2) — 러닝 연속성 (2026-11-24)

`세션` · **제안** — D-33을 바꾼다. 방향은 D-45(정함). 비교군·시나리오·임계값은 PREREG를 얼리기 전까지 제안
- **결정** — 0단계 운영 증거부터 모은다. 비교군 A(지금)·A′(고친 모놀리스)·B0(같은 jar 두 함대)·B2(실제 분리), 핵심 비교는 A′ vs B2. 핵심 시나리오 SC1–SC7. 새 서버 지표는 metrics-convention 예정 행으로 코드보다 먼저 적는다(이름이 겹치는 것은 정하지 않음).
- **버린 대안** — v1(D-33) — 성능 중심이라 분리 이유(안정성, D-45)를 재지 않는다 / 비교군 B1(실시간 상태만 다른 Redis) — D-49로 목표에 이미 들어 있고 SC4는 A′R2로 본다
- **이유** — [running-session-server/verification.md](running-session-server/verification.md) 「비교군」 「핵심 시나리오 SC1–SC7」
- **영향** — verification.md, load_test/msa/, docs/metrics-convention.md

### D-58 결과 조회(6-1·6-2)는 세션이 응답 (2026-11-24)

`세션` · **정함** — D-46을 바꾼다
- **결정** — 끝낸 직후 결과는 러닝 세션 서버가 ALB 경로 규칙으로 응답한다(공개 주소·계약 그대로). 메인의 7-1·기록 카드는 사본에 반영된 뒤 보인다. 복제는 대조(러닝 DB vs 메인 사본 누락 0)로 확인한다.
- **버린 대안** — 메인이 응답(D-46)과 그 (가)·(나) — 끝낸 직후 읽기가 깨지거나, 동기 대체나 API 변경이 필요하다
- **이유** — saga도 동기 호출도 없다. 카드 생성은 경합하지 않는다 — `runningRecordId`는 메인이 사본으로 내는 응답(7-1 · 홈 활동 · 8-1)으로만 앱에 가고 6-1 · 6-2에는 없다
- **영향** — architecture 「결과 조회」 「공개 경로」, api-spec 6-1·6-2(주인 표시)·7-1(늦게 보일 수 있다는 메모) — 전환 때

### D-59 문서 계획 v1 · v2 · v3 (2026-11-24)

`문서` · **바뀜 → D-60**
- **결정** — v1: running-session-split.md ADR 하나 + load_test/msa의 PREREG·EVIDENCE. v2: running-session/{README, design, verification, migration} + ai-server/README. v3: 서비스 폴더마다 decisions.md를 두고, 저장소를 나눌 때 폴더를 통째로 옮긴다.
- **이유** — 그때의 제안

### D-60 docs/msa 구조와 이름 (2026-11-24)

`문서` · **정함** — D-59를 바꾸고 D-08을 넓힌다
- **결정** — 루트에 README·decisions(하나)·platform·eks·kafka·cdc·outbox-inbox. 서비스 폴더는 `ai-server/`(README·rationale·architecture)와 `running-session-server/`(README·rationale·architecture·consistency·runtime·verification·migration). 측정은 `load_test/msa/`(README·PREREG·RUNBOOK·RESULTS). 저장소를 나눌 때 옮기는 것은 runtime.md와 erd에 잠시 두는 「러닝 DB」 장뿐이다(스키마는 새 저장소의 Flyway가 이어받는다, D-56). 모놀리스는 '메인 서버 (running-service)'라 부르고 폴더를 두지 않는다.
- **버린 대안** — v1·v2·v3(D-59) — 질문마다 집이 하나가 아니거나, 서비스마다 결정 번호가 겹치거나, 메인이 쥐어야 할 경계 문서가 저장소를 따라 나간다
- **이유** — 설계 검토안대로 쓰기로 했다 ([README.md](README.md) 「문서 규칙」)
- **영향** — docs/msa 전체, load_test/msa/, CLAUDE.md·루트 README 문서 색인

### D-61 러닝 세션 서버의 실제 이름 (2026-11-24)

`공통` · **제안**
- **결정** — 저장소·이미지·Kafka 계정은 `running-session-server`(계정 `{env}.running-session-server`), 별도 프로세스 호스트는 `running-session-app`. 모듈·패키지 이름은 D-35.
- **버린 대안** — 폴더 이름을 저장소 이름으로(v3) — 폴더는 메인 저장소에 남고, AI도 실제 이름(`runiverse-ai`)이 폴더와 다르다
- **이유** — 도표 이름('러닝 세션 서버')과 맞고 `running-service`와 헷갈리지 않는다
- **영향** — [README.md](README.md) 「서비스 이름」, platform 「새 이름」

### D-62 저장소 옮기기(M6)를 운영 추출(M7) 앞에 (2026-11-24)

`세션` · **제안**
- **결정** — test 검증(M5) 뒤 바로 별도 저장소로 옮기고, 운영 추출은 새 저장소의 배포물로 한다. 되돌리기를 위해 모놀리스는 그 배포물을 계속 담는다.
- **버린 대안** — 운영 추출이 안정된 뒤, 옛 엔진을 지우기 직전에 옮기기 — 운영에서 검증한 것이 실제로 떼어 낸 코드가 아니게 된다
- **이유** — 정한 순서(검증 → 저장소 분리 → 옛 코드 삭제)를 그대로 따른다
- **영향** — [running-session-server/migration.md](running-session-server/migration.md) 「단계 M0–M9」

### D-63 끝내기는 새 엔진에서 — 잠금 밖 S3 · 날씨, 짧은 트랜잭션 (2026-11-25)

`세션` · **정함** — D-32와 D-34의 F1을 바꾼다
- **결정** — 끝내기 구조는 옛 엔진에서 고치지 않고 러닝 세션 서버의 새 엔진(M3)에서 짠다. 잠금 · 트랜잭션 밖에서 Redis 트랙 읽기 → 분석 → S3 원본 트랙 업로드 → 날씨 조회(지금처럼 출발 좌표 · 출발 시각 기준, 실패하면 기본값)를 먼저 하고, 짧은 트랜잭션에서 참가자 → 러닝 세션 잠금 → 이미 끝났는지 다시 확인 → 기록 · 구간 · 상태 · 아웃박스를 커밋한다. 원본이 먼저 S3에 있으니 커밋이 실패해도 다시 끝내기로 복구된다.
- **버린 대안** — 옛 엔진에서 먼저 고치기(D-34의 F1) — 세션 서버에서 아웃박스 발행 등과 함께 다시 짤 코드라 두 번 일한다 / 날씨를 러닝 시작 때 미리 받기 — 시작 때는 아직 출발 좌표가 없고, 슬롯이 같은 초에 시작해 호출이 몰린다 / 넘기기 때 받기 — 원격 러닝이라 사람마다 위치가 다르다 / 메인이 반영 뒤 채우기 — 날씨 열 NOT NULL을 바꾸는 손 DDL과 9-2 카드와의 시점 문제 / 스케줄러가 1시간마다 받아 DB에 두기 — 테이블 · 스케줄러를 새로 만드는 큰 작업이다
- **이유** — 날씨 값이 지금과 같아 api-spec 5-D · 9-5와 erd가 바뀌지 않는다. 외부 호출이 커넥션 · 잠금 밖으로 나가 run 14의 원인이 사라진다
- **대가** — 새 엔진이 나올 때까지 운영과 되돌릴 곳(옛 엔진)에 run 14 구조가 남는다. 지금 규모로는 400명이 한 순간에 끝나는 일이 드물다(1분에 퍼지면 모두 성공 — load_test/RESULTS.md run 15)
- **영향** — [running-session-server/runtime.md](running-session-server/runtime.md), migration 「선행 수정 F1–F9」 「단계 M0–M9」, architecture 「메인 사본과 반영」 「정하지 않은 것」

### D-64 끝내기 폭주는 속도가 아니라 격리로 잰다 + 매칭↔러닝 정합성 (2026-11-25)

`세션` · **정함** — D-57의 SC9 · SC10을 바꾸고 SC14를 더한다
- **결정** — 끝내기 폭주(G4)를 '나누니 빨라졌다'의 근거로 쓰지 않는다. 나눈 효과는 격리로 잰다 — 세션을 용량 이상으로 몰아도 메인(매칭 · 7-1 등)이 평소와 같은가(SC9), 메인을 포화시켜도 러닝이 평소와 같은가(SC10). 둘 다 G3에 넣는다. 매칭↔러닝 경계의 정합성 — 넘기기가 시작 전에 닿는가, 시작 뒤 나가기와 시작이 겹칠 때 하나로 정해지는가, 끝낸 직후 다시 신청할 수 있기까지 — 을 SC14로 잰다.
- **버린 대안** — 끝내기를 고친 모놀리스(A′)와 분리(B2)의 속도 비교 — 끝내기를 새 엔진에서만 고치므로(D-63) 고친 효과와 나눈 효과가 섞인다
- **이유** — 매칭과 러닝은 같은 행보다 같은 커넥션 풀에서 부딪힌다. 방은 모집(매칭이 잠금) → 러닝(러닝이 잠금)으로 단계가 넘어가 같은 행을 동시에 잡는 일은 시작 뒤 나가기 정도다. 나누면 잠금 충돌 대신 서비스 사이 정합성이 검증 대상이 된다
- **영향** — [running-session-server/verification.md](running-session-server/verification.md) 「보조 시나리오 SC0 · SC8–SC14」 「판정과 결과」, [load_test/msa/PREREG.md](../../load_test/msa/PREREG.md) SC9 · SC10 · SC14, rationale 「G3 장애 격리」 「G4 끝내기 폭주는 고칠 결함」
