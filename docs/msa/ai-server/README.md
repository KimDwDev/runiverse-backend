# AI 전용 서버

AI 전용 서버(저장소·Kafka 계정 `runiverse-ai`)는 기록 카드 음악을 만든다. 주인은 AI 쪽이고, 메인 서버(running-service)와는 Kafka 요청·완료 토픽으로만 주고받는다. 이 폴더는 메인 쪽에서 본 AI 서버를 적는다 — 왜 따로인지, 경계와 흐름, 지금 어디까지 되는지, AI 쪽에 무엇을 기다리는지. AI 서버 안의 설계는 AI 저장소가 맡는다.

> 상태: 운영 중 — 요청 발행과 완료 구독은 test·prod에 배포됐고, 실제 완료 반영은 AI 쪽 헤더 `id`를 기다린다. AI 쪽이 바꿨다고 알리거나 왕복을 확인하면 그 PR에서(또는 알게 된 바로 뒤) 고친다.

| 무엇 | 어디 |
|---|---|
| 이름 — 문서 이름·저장소·Kafka 계정 | [README.md](../README.md) 「서비스 이름」 |
| 왜 따로인가 | [rationale.md](rationale.md) |
| 경계·흐름·완료 발행·실행 환경·러닝 세션 분리의 영향 | [architecture.md](architecture.md) |
| 이벤트 계약 (정본) | [api-spec.md](../../api-spec.md) 9-5 |
| 받는 쪽 규칙·직접 확인할 것 | [outbox-inbox.md](../outbox-inbox.md) 「받는 쪽 — Inbox」 「검증」 |
| 토픽·계정·그룹 이름과 권한 | [kafka.md](../kafka.md) 「이름」 「ACL」 |
| 결정 | [decisions.md](../decisions.md) 범위 `AI` — D-02 · D-03 · D-05 · D-07 · D-09 · D-11 · D-14 ~ D-16. 메인 쪽 받기 D-12 · D-13, 공통 D-04 · D-06 |

> 주소·키·비밀값은 적지 않는다 — Parameter Store 이름까지만.

## 지금 상태

11-24 기준.

| 단계 | 상태 |
|---|---|
| 요청 발행 — 아웃박스 → Debezium → 요청 토픽 | 됨 — [README.md](../README.md) 「지금 운영 중인 것」 |
| AI 서버 수신 | test 됨 — 11-23 실제 카드가 AI 서버까지 가고 카드·사진 ID가 그대로 돌아왔다. prod 수신은 AI 쪽 확인 |
| AI 서버 inbox — 요청 헤더 `id`로 거르기 | AI 쪽 확인 전 ([decisions.md](../decisions.md) D-09 제안) |
| 메인 완료 구독 + inbox | 됨 — [README.md](../README.md) 「지금 운영 중인 것」 |
| AI 음악 엔드포인트 | 대기 (AI 쪽) — 11-23 왕복에서 없어 결과가 `FAILED`(`INVOKE`)였다 |
| 완료 반영 — AI 완료 → 메인 음악 상태 | 대기 (AI 쪽) — 완료 이벤트에 헤더 `id`가 없고 이름이 `inference_id`라 메인이 계약 위반으로 건너뛴다. 그동안 실제 음악은 `GENERATING`에 남는다 |
| EKS 위 AI 서버 (E6) | 상태는 [running-session-server/README.md](../running-session-server/README.md) 「진행 단계」 E6 줄 |

- **11-23 왕복의 경로와 결과는 [outbox-inbox.md](../outbox-inbox.md) 「검증」에 있다** — AI 쪽이 고치면 그 표 마지막 줄을 같은 방법으로 test → prod 순서로 확인하고 이 표를 고친다.
- 로컬은 AI 없이 가짜 완료로 돈다 — [outbox-inbox.md](../outbox-inbox.md) 「로컬 가짜 AI (음악 완료)」 ([decisions.md](../decisions.md) D-13).

## 기다리는 것

AI 쪽이 해야 메인이 다음으로 간다. 받으면 「지금 상태」를 고친다.

1. **완료 이벤트의 헤더 `id`(= `inference_id`)** — 없으면 메인이 받지 않는다 ([api-spec.md](../../api-spec.md) 9-5 완료 이벤트, [outbox-inbox.md](../outbox-inbox.md) 「처리할 수 없는 메시지」, [decisions.md](../decisions.md) D-12).
2. **AI 음악 엔드포인트** — 11-23 왕복이 `FAILED`(`INVOKE`)로 끝났다.
3. **prod 수신 확인.**
4. **요청 이벤트를 헤더 `id`(= `outbox_event_id`)로 거르는지** — api-spec 9-5 요청 이벤트 「아직 정하지 않은 것」 4, D-09.
5. **음악 버킷 이름 (test·prod)** — 「정하지 않은 것」 1.
6. **계약에서 남은 것** — `failed_stage` 값 전체, 파일 형식은 [api-spec.md](../../api-spec.md) 9-5 「아직 정하지 않은 것」에 둔다(여기 옮기지 않는다).

## 정하지 않은 것

계약 밖 항목만 둔다. 계약 항목은 [api-spec.md](../../api-spec.md) 9-5 「아직 정하지 않은 것」, 완료 발행 방식과 AI DB 자리는 [architecture.md](architecture.md) 「정하지 않은 것」.

1. **음악 버킷 이름과 앱 읽기 권한 범위** — 이름을 아직 받지 못했다. 앱은 `S3_MUSIC_BUCKET`이 없으면 기동하지 않아, 배포 전에 test·deploy Parameter Store에 넣고 앱 역할에 읽기 권한을 준다([decisions.md](../decisions.md) D-14). 음악 key가 `audio/{env}/`로 시작하므로(api-spec 9-5 완료 이벤트 예시) 권한을 버킷 전체로 줄지 그 접두어로 좁힐지 함께 정한다. 버킷 목록은 [platform.md](../platform.md) 「S3」.
2. **AI가 환경마다 계정을 따로 쓰는지, dev·test를 한 대로 겸하는지** (D-04) — 이름은 환경마다 있다(`{env}.runiverse-ai`, [kafka.md](../kafka.md) 「이름」). 한 대가 겸하면 두 계정·두 그룹으로 붙어야 한다.
3. **AI 서버가 컨슈머 그룹 하나로 받는지** — 권한은 그룹 `{env}.runiverse-ai.record-card-music`에만 있다([kafka.md](../kafka.md) 「ACL」). AI 서버가 여러 대가 되어도(EKS에서 늘리기 포함) 이 그룹 하나로 받아 요청 하나를 한 대만 처리하는지 AI 쪽 확인 전.
4. **지운 카드·사진이 AI DB에 남는 것** — 메인은 지우는 이벤트를 보내지 않는다(9-3에서 뺀 사진, 9-4 카드 삭제). AI DB의 그 행을 지울지 AI 쪽 답 전.
5. **AI 클라이언트 설정** — Python 프로듀서에 `enable.idempotence`를 명시했는지([kafka.md](../kafka.md) 「보내는 쪽은 중복 방지를 켠다」), 그 환경 클러스터의 CA 인증서를 넣었는지([kafka.md](../kafka.md) 「CA 인증서 배포」) — AI 쪽 확인 전.
