# 러닝 세션 서버 — 옮기기

러닝 세션을 메인 서버(running-service)에서 떼어 내는 순서다. 기존 코드를 지우지 않고 옆에 새로 만들어 검증하고, 별도 저장소로 옮긴 뒤 옛 코드를 지운다. 지금 코드가 어디서 얽혀 있는지, 무엇을 먼저 고치는지, 단계마다 무엇을 하고 무엇이 통과해야 다음으로 가는지, 어떻게 되돌리는지, 언제 지우는지를 적는다.

| 무엇 | 어디 |
|---|---|
| 단계 진행 상태 | [README.md](README.md) 「진행 단계」 |
| 목표 구조 — 경계 · 데이터 주인 · 넘기기 · 이벤트 · 공개 경로 | [architecture.md](architecture.md) |
| 불변식 · 탈퇴 saga · 대조 | [consistency.md](consistency.md) |
| 세션 안 동작 — 끝내기 · 계획 종료 | [runtime.md](runtime.md) |
| 비교군 · 시나리오 · 판정 | [verification.md](verification.md) |
| 기준 숫자 (`K-…`) | [load_test/msa/PREREG.md](../../../load_test/msa/PREREG.md) |
| EKS 단계 · 파드 종료 값 | [../eks.md](../eks.md) 「도입 단계 E0–E7」 「계획 종료」 |
| 결정과 바뀐 이력 | [../decisions.md](../decisions.md) |
| 레이어 · 패키지 · 다중 인스턴스 | 레이어 규칙 ([docs/architecture.md](../../architecture.md)) |

> 상태: 계획 — 단계 상태는 [README.md](README.md) 「진행 단계」에만 둔다. 절마다 `운영 중` · `정함 (D-xx)` · `제안` · `정하지 않음`을 단다. 「지금 얽힌 곳 (11-24 기준)」만 지금 코드다.

> 주소·키·비밀값은 적지 않는다 — Parameter Store 이름까지만.

- **`- [ ]`는 할 일 단위다** — 그 일을 끝낸 PR이 체크한다. 상자가 다 차도 단계는 README 「진행 단계」에서 닫는다.
- **나가는 조건의 숫자는 PREREG에만 둔다** — 여기서는 `K-SCn-*` id로 가리킨다. 0이어야 하는 불변식(I1 · I5)과 기간(주 · 슬롯 저녁 수)은 여기 적는다.
- **나가는 조건 · 지울 기준을 바꾸려면 결정부터** — [../decisions.md](../decisions.md)에 항목을 먼저 쓰고 같은 PR에서 이 문서를 고친다.
- **이 문서는 메인 저장소에 남는다** — 옛 코드는 메인 저장소에서 지운다. M8 뒤에는 옮긴 기록으로 남는다.

## 방식

`정함 (D-22, D-47, D-62, D-78)` — 지금 코드는 지우지 않고, 같은 저장소에 별도 앱을 새로 만들어 compose와 test에서 검증한 뒤 별도 저장소로 옮기고 운영을 전환한다. 단계는 「단계 M0–M9」.

- **지우지 않고 옆에 새로 만든다** — 지금 러닝 세션 코드(이하 옛 엔진)는 M7까지 운영을 맡고 M8에 지운다.
- **처음부터 따로 띄운다** — 새 코드는 루트의 `running-session-server/`에 별도 Spring Boot 앱으로 만든다. 모놀리스 안에서 호스팅하지 않는다(D-78).
- **애그리거트를 쪼개지 않고 새로 만든다** — 매칭과 러닝은 한 애그리거트다(「지금 얽힌 곳 (11-24 기준)」). 메인 쪽은 넘기기 이벤트를 쓰고 결과를 받는 일만 더한다.
- **선행 수정은 모놀리스에 먼저** — F1(끝내기 구조) · F3 · F5–F9 — `정함 (D-65, D-66, D-79)`. F2는 버림(D-80), F4는 버림(D-84). 새 엔진의 끝내기도 같은 구조다(D-63, D-82).

## 지금 얽힌 곳 (11-24 기준)

`운영 중` — 11-24 코드를 센 것이다. 목표 쪽 결론은 [architecture.md](architecture.md) 「경계 — 생애 단계」.

**러닝과 매칭이 한 애그리거트다**

| 얽힌 곳 | 지금 코드 | 옮길 때 |
|---|---|---|
| 애그리거트 | `RunningRoom`이 모집(`join` · `leave` · `invite` · `acceptInvitation` · `closeMatching`)과 러닝(`start` · `rejoin` · `finishSession` · `finish` · `cancel`)을 함께 들고 `RoomSession` · `RoomInvitation`을 품는다 | 새 엔진은 자기 러닝 세션 모델을 갖는다. 옛 클래스는 M8에서 러닝 메서드만 줄인다 |
| 어댑터 | `RunningPersistenceAdapter`가 러닝 포트 13개 + 매칭 포트 9개 + `LoadUserStatusPort`를 구현한다 — 주석 '매칭 유스케이스가 자기 포트로 같은 애그리거트를 다룬다' | M8에서 러닝 포트를 뺀다 |
| 잠금 | 참가자 → 방 순서(레이어 규칙). 시작 · 취소 · 끝내기 · 강제 종료 · 탈퇴 정산이 같은 행을 잠그고, 방 행은 매칭 배정 · 초대 · 모집 마감 · 채팅도 잠근다. 매칭과 러닝 사이에 선을 그으면 같은 방 · 참가자 행을 쓰는 트랜잭션이 매칭 10개 + 러닝 7개다 | 생애 단계로 나눠 단계마다 쓰는 쪽이 하나다 (D-26) |
| import | `application/match` → `domain.running` 38파일, `application/running` → `application/match` 4파일(예: `SettleRunningForAccountDeletionHandler`), 반대 7파일(예: `SendInvitationHandler`). running → recordcard 2, recordcard → running 1, user → running 1 | 새 엔진은 메인을 import하지 않는다(「경계 검사」) |
| 공유 VO · 포트 | `Pace` · `AvgPace` · `RunningRoomId` · `RunningRecordId`. `UserPersistenceAdapter`(포트 약 30개)가 끝내기에 `LoadUserWeightPort` · `UpdateUserAvgPacePort`를 준다 | 몸무게는 넘기기 스냅샷, 평균 페이스는 메인 inbox (architecture 「데이터 주인」) |
| 끝내기 안 정책 | `RunningFinisher`가 페널티 · 쿨다운(`StartMatchCooldownPort`) · 평균 페이스 · 몸무게 · 날씨(`LoadWeatherPort`)를 한 트랜잭션에서 한다 | 「선행 조건」 — 정책을 끝내기 밖 반응으로 뺀다 |
| 오류 코드 | application `ErrorCode`가 sealed(`permits` 11개)이고 `GlobalExceptionHandler.toStatus()`가 빠짐없이 가른다. 다른 Gradle 프로젝트는 끼지 못해 그 예외가 일반 처리기로 떨어져 500이 되고 `ErrorExposurePolicy`가 가린다. `RunningWebSocketHandler`가 `BusinessException` · `ErrorCode`를 import한다 | 모듈 자기 오류 코드 + WS 오류 변환기(「원격처럼」) |
| Redis 리스너 | `runningChannelContainer` 빈 하나(`RunningChannelConfig`, 실행기 지정 없음)를 `MatchChannelConfig` · `NotificationChannelConfig` · `ScheduleChannelConfig` · 방 구독자가 이름으로 받는다 | 두 번째 컨테이너 빈이 생기면 주입이 모호해진다(「원격처럼」) |
| 예약 작업 | `scheduled_jobs` 한 테이블, 채널 `schedule:job` 하나. 실행기는 매칭 셋(`MATCH_CLOSE` · `RUNNING_READY` · `RUNNING_REMINDER`)과 러닝 둘(`RUNNING_START` · `RUNNING_FORCE_FINISH`). `RunScheduledJobHandler.executorOf`는 실행기 없는 종류에 `IllegalStateException`을 던진다 | 서비스마다 자기 테이블 · 채널 (D-29) |
| 쿨다운 | `MatchCooldownRedisAdapter`가 매칭 `MatchCooldownPort`와 러닝 `StartMatchCooldownPort`를 함께 구현한다(키 `match:cooldown:{userId}`) | 정하는 쪽 세션, 지키는 쪽 메인 (architecture 「데이터 주인」) |
| 아웃박스 | `OutboxEventJpaEntity`의 `ck_outbox_event_aggregate_type`은 `RECORD_CARD`만, 토픽 CHECK는 `(dev\|test\|prod).record-card.music.requested`만 받고 `AggregateType`도 하나다. 같은 CHECK가 손으로 쓴 test · prod DDL에 있다. 쓰는 어댑터는 `RecordCardMusicOutboxAdapter` 하나 | 「아웃박스 일반화」 |
| 메모리 이벤트 | 커밋 뒤 Spring 이벤트 8종(예: `RunningConnectionCloseRequestedEvent`)은 인스턴스가 죽으면 사라진다 | 경계를 넘는 것은 아웃박스나 `scheduled_jobs`처럼 다시 살릴 수 있는 길로 |
| 보안 · WS | HS256 비밀값 하나(`JwtEncoderConfig` · `JwtDecoderConfig`, 블랙리스트 `BlockedTokenValidator`). `JwtHandshakeInterceptor`가 모놀리스 필터 체인에 기대고 `AppVersionFilter`가 WS 업그레이드에도 돈다. 레이어 규칙 ([docs/architecture.md](../../architecture.md))이 허용한 직접 import `WebSocketConfig` → `JwtHandshakeInterceptor` · `RunningWebSocketHandler` | architecture 「인증」 (D-30), 「새 저장소가 따르는 규칙」 |
| 검사 | `settings.gradle`은 루트 프로젝트 하나. `check_conventions.py`는 레이어 방향 · legacy 격리 등을 보고 모듈 경계는 보지 않으며 CI에서 돌지 않는다. ArchUnit · Modulith · Flyway 없음 | 「앱 모양」 「경계 검사」 |

**메인이 러닝 테이블을 읽는 곳** — 새 엔진 방의 기록이 러닝 DB에만 생기기 전에(compose 두 앱 E2E 전, 운영은 M7 전) 모두 사본이나 허용 목록 호출로 바꾼다. 사본은 지금 `running_records` · `running_segments` 그대로다 — 정함 (D-68, [architecture.md](architecture.md) 「메인 사본과 반영」).

| 읽는 곳 | 지금 코드 | 분리 뒤 |
|---|---|---|
| 기록 카드 FK · 잠금 | `record_cards.running_record_id` → `running_records`(`fk_record_card_running_record`, `ON DELETE CASCADE`, `uk_record_card_running_record`). 7개 트랜잭션이 기록 행을 `PESSIMISTIC_WRITE`로 잠근다 — `RecordCardDraftSaver.save`(`LockOwnRunningRecordPort.lockOwnedBy`), `RecordCardEditDraftSaver.save` · `RecordCardEditor.applyNow` · `RecordCardEditor.checkBeforeDraft` · `DeleteRecordCardHandler` · `RetryRecordCardMusicHandler` · `CompleteRecordCardMusicHandler`(`LoadRecordCardForUpdatePort.loadForUpdate`) | 메인 사본 행을 가리킨다 |
| 7-1 · 카드 상세 | `RunningRecordPersistenceAdapter.loadByPeriod`가 `record_cards`를 LEFT JOIN한다(`activityType`, `21427ea8`). `RecordCardPersistenceAdapter.loadDetail`이 세그먼트 · 세션 · 참가자를 조인한다 | 사본과 카드가 메인 안에 있어 그대로 조인 |
| 함께 뛴 사람 추천 | `FriendRecommendationPersistenceAdapter.loadRanTogether`가 JPQL 문자열 안에서 `RunningRoomSessionJpaEntity` · `RunningPlayerJpaEntity`를 조인한다 — import 검사에 걸리지 않는다 | 사본 |
| 홈 활동 | `HomeNoticeBroadcastRunner` → `RunningActivityReader`(지금 달리는 수 · 최근 셋 · 오늘 · 어제 완주자). `AppStreamEvent`가 `RunningActivity`를 import한다 | 달리는 수는 허용 목록 5, 나머지는 사본 |
| 사용자 상태 | `GetUserStatusHandler` · `SendInvitationHandler` · `RoomChatAccessPolicy`가 `LoadUserStatusPort`를 쓴다. `SendInvitationHandler`는 같은 트랜잭션에서 초대 알림을 쓴다 | 사본 + 허용 목록 4 |
| 결과 조회 6-1 · 6-2 | `GetRunningResultsHandler` · `GetRunningSplitResultsHandler`가 메인 사용자 데이터로 프로필을 채운다 | 세션의 프로필 투영 (D-58) |

**지금 탈퇴 경로** — 목표 saga는 [consistency.md](consistency.md) 「계정 탈퇴 saga」.

- **한 트랜잭션이다** — `DeleteAccountHandler.handle`(`@Transactional`): 사용자 잠금 → 스냅샷 → `SettleRunningForAccountDeletionHandler`(참가자 → 방 잠금. 시작 전이면 나가기, 시작했는데 뛰지 않았으면 이탈, 러닝 중이면 끝내기 경로를 그대로 불러 S3 업로드 · 날씨 조회가 탈퇴 트랜잭션 안에서 돈다) → 탈퇴 스냅샷 · 기기 · 사용자 삭제(`ON DELETE CASCADE`) → refresh 토큰 삭제 · access 토큰 차단.
- **트랙을 못 읽으면 전부 롤백된다** — `RunningTrackUnavailableException`을 `AccountDeletionUnavailableException`(503 `ACCOUNT_DELETION_UNAVAILABLE`)으로 바꿔 던진다.
- **`FlushPendingChangesPort`** — 초대를 `LEFT`로 바꾸는 갱신을 커밋까지 미루면 CASCADE가 그 행을 먼저 지워 갱신이 실패하므로 바로 DB에 쓴다.
- **소켓은 같은 인스턴스만 닫힌다** — 커밋 뒤 `RunningConnectionCloseListener`가 그 인스턴스의 연결만 닫는다.
- 초대 방에 혼자 남아 탈퇴하면 500이 나던 버그는 PR #116(11-02)에서 고쳤다.

## 앱 모양

`정함 (D-78)` — 같은 저장소의 별도 앱. 이름은 D-61(제안).

```text
runiverse-backend/
├── running-service/            메인 + 옛 엔진 — 지금 그대로
├── running-session-server/     새 러닝 세션 앱 (Spring Boot, 자기 build.gradle · Dockerfile)
│   └── src/main/java/com/runiverse/running_session/
│       └── presentation · application · domain · infrastructure
├── compose.yml                 dev — 두 앱 + Postgres 둘 + Redis 둘 + Kafka · Debezium + 경로 프록시
└── e2e_test/                   경로 프록시 주소 하나로 두 앱을 함께 시험한다
```

| compose 서비스 | 포트 (예) | 비고 |
|---|---|---|
| 메인 `running-service` | 8080 | |
| 세션 `running-session-server` | 8090 | 8081은 관리 포트(「선행 조건」) |
| 경로 프록시 (nginx) | 80 | 운영 ALB 경로 규칙을 흉내 낸다 — 러닝 WS · 6-1 · 6-2 · (정하면) 확정 뒤 이탈 → 세션, 나머지 → 메인 ([architecture.md](architecture.md) 「공개 경로」) |
| Postgres 둘 | | 서비스 DB · 러닝 DB |
| Redis 둘 | | 메인 · 세션 (D-49) |
| Kafka + Debezium | | 방 확정 · 참가자 결과가 실제로 오간다 |

- **메인 코드를 import하지 않는다** — 별도 프로젝트라 의존할 수 없다. 같이 쓰던 코드(`TrackFilter` · `TrackAnalyzer` · 폴리라인 · JWT 검증)는 복사하고 골든 트랙 동등성 테스트로 맞춘다.
- **저장소를 나눌 때 폴더를 그대로 옮긴다** — 「단계 M0–M9」 M6.

## 원격처럼

`정함 (D-78)` — 처음부터 따로 띄우므로 저절로 지켜진다. 남은 것은 메인과 말하는 길을 이벤트와 허용 목록으로만 두는 것이다.

| 무엇 | 세션 앱이 갖는 것 |
|---|---|
| DB | 자기 러닝 DB — dev는 compose의 두 번째 Postgres (D-78), test 자리는 [../platform.md](../platform.md) 「test 환경」 |
| Redis | 자기 키 접두어 · 채널 · 리스너 컨테이너와 연결 설정. 세션 Redis(D-49)가 생기면 설정만 바꾼다 — 옮기는 창은 [../platform.md](../platform.md) 「Redis」 |
| 오류 | 자기 오류 코드 + api-spec의 코드 문자열을 그대로 내는 WS 오류 변환기 |
| 메인과 말하기 | 아웃박스 · inbox와 허용 목록 호출([architecture.md](architecture.md) 「동기 호출 허용 목록」)뿐. 메인 빈 · 포트를 부르지 않는다 |
| 사용자 데이터 | 넘기기 스냅샷 + 프로필 투영 — 사용자 테이블을 읽지 않는다 |
| 예약 작업 | 자기 `scheduled_jobs` · 채널, DB 행 점유로 한 번만 실행 (D-29) |

## 경계 검사

`정함 (D-54)` — 규칙 목록은 `제안`. [check_conventions.py](../../../.claude/skills/spec-check/scripts/check_conventions.py)에 `check_legacy_isolation`과 같은 방식으로 더하고, M1부터 CI에서 PR마다 돌린다. 나가는 조건은 위반 0.

| # | 규칙 | 막는 것 |
|---|---|---|
| 1 | 세션 앱 → 메인 import 금지 — 별도 Gradle 프로젝트라 의존할 수 없다(D-78) | 추출이 다시 쓰기가 되는 것 |
| 2 | 메인 → 세션 앱 import 금지 — 별도 프로젝트라 의존할 수 없다(D-78). 메인은 이벤트 계약과 허용 목록 클라이언트로만 닿는다 | 메인이 세션 안쪽에 기대는 것 |
| 3 | 옛 엔진 목록(검사가 읽는 파일)의 클래스를 새 파일이 import하면 위반 | 옛 엔진을 지울 수 없게 되는 것 |
| 4 | 옛 엔진 목록의 파일을 고친 PR은 `mirrored` 라벨이나 `running-session-server/` 쪽 변경이 있어야 한다. PR 템플릿에 체크 상자를 더한다 | 한 엔진만 고치는 것(「병행 유지」) |
| 5 | JPQL · SQL 문자열 안의 엔티티 · 테이블 이름도 본다 — 다른 쪽 엔티티를 조인하면 위반 | `loadRanTogether`처럼 import를 피한 결합 |
| 6 | 서비스 사이 동기 호출 클라이언트는 허용 목록에 있는 것만 | 목록 밖 호출 (D-52) |

## 선행 수정 F1–F9

M0에서 모놀리스에 먼저 넣는 것은 F1 · F3 · F5–F9 — `정함 (D-65, D-66, D-79)`. F2는 버림(D-80), F4도 버림(D-84). 모놀리스에 넣은 묶음이 비교군 A′다([verification.md](verification.md) 「비교군」). 앱은 바꾸지 않는다 — 다시 붙을 때 전체 트랙 대신 `nextSequence`부터 보내기는 앱 동작이 바뀌는 일이라 넣지 않는다.

| F | 고칠 것 | 지금 코드 |
|---|---|---|
| F1 | **M0 모놀리스 — 이번 PR (D-79)** — 끝내기를 판정 → 봉인 → 확정으로 나눈다 (D-82). 임대(판정 앞, Redis `SET NX` TTL 30초) → 판정(잠금 안): 다시 확인 · 트랙 스냅숏 · `TrackFilter` · 목표 판정. 봉인(잠금 밖, 임대만): `TrackAnalyzer` · S3 원본 업로드 · 날씨. 확정(잠금 안): 다시 확인 → 기록 · 구간 · 상태 저장. 임대를 본 `RUNNING_FINISH`는 `RUNNING_TRACK_UNAVAILABLE`, 자동 종료는 무응답. 원본 트랙 업로드 요청에만 `apiCallTimeout` 10초. 새 엔진도 같은 구조로 짠다 | `RunningFinisher.confirm`이 잠금을 쥔 채 `createRecord`에서 S3 업로드 · 날씨 조회를 기다린다(코드의 TODO) |
| F2 | **버림 (D-80)** — 계산마다 잠근 채 판정한다. 아래는 버린 안: 목표 도달 사전 검사 — 필터 거리를 트랜잭션 밖에서 먼저 보고, 닿았을 때만 확정 트랜잭션을 연다 | `UpdateRunningFinishJudge` → `RunningFinisher.finishOnGoal`이 목표를 넘긴 배치마다 `FOR UPDATE` 트랜잭션을 연다 |
| F3 | **M0 모놀리스 (D-66, D-83)** — 강제 종료를 알람(잠금 없이 확인 → 할 일 있을 때만 잠금 → 남으면 1분 뒤 다시 걸기) + 러너마다 워커(`RunningFinisher.finish`)로 | `ForceFinishRunningRoomHandler`가 방 전체를 트랜잭션 하나로 닫는다 |
| F4 | **버림 (D-84)** — `app_versions` 30초 캐시. 버전 확인은 쿼리 하나 동안만 커넥션을 빌리고, 풀을 바닥내던 원인은 F1 · F3가 없앴다. 커넥션 대기는 F5 지표로 본다 | `AppVersionFilter`가 WS 핸드셰이크를 포함한 요청마다 잠금 없이 읽는다 — 그대로 둔다 |
| F5 | **M0 모놀리스 (D-66)** — Hikari를 명시한다 — 풀 크기(비교군 사이 DB 연결 총량을 같게), `connectionTimeout`은 앱의 끝내기 대기보다 짧게, 누수 감지 | `reWriteBatchedInserts`만 설정돼 있다 |
| F6 | **M0 모놀리스 (D-66)** — liveness = `livenessState`, readiness = `readinessState`(db · redis 뺌), 대상 그룹 검사는 readiness | 기본 health 집계에 db · redis가 들고, `SecurityConfig`는 `/actuator/health` · `/actuator/prometheus` 정확한 경로만 인증 없이 연다(ALB 규칙이 health만 통과시킨다) — `/actuator/health/liveness` · `/readiness` 그룹 경로는 열려 있지 않다 |
| F7 | **M0 모놀리스 (D-66)** — `running:room:*` 전용 리스너 컨테이너와 상한 있는 실행기 | `RunningChannelConfig`에 실행기 지정이 없다 |
| F8 | **M0 모놀리스 (D-66)** — `JAVA_TOOL_OPTIONS`로 `MaxRAMPercentage` — 값은 「정하지 않은 것」 4 | Dockerfile `ENTRYPOINT`에 JVM 옵션이 없다 |
| F9 | **M0 모놀리스에 먼저 (D-65)** — WS 계획 종료(ASG) — readiness 내림 → 새 `RUNNING_START` 거절 → SSE 완료 → 소켓을 `4500`으로 나눠 닫기(시간은 [runtime.md](runtime.md)) → graceful 종료. ASG 쪽 값: `docker stop -t 60`, 대상 그룹 등록 해제 지연 60초 | `server.shutdown` 설정도, 소켓을 닫는 종료 코드도 없다 |

- **앱 쪽 순서와 시간**은 [runtime.md](runtime.md), EKS 파드 값은 [../eks.md](../eks.md) 「계획 종료」. 닫기 코드는 `4500`을 다시 쓴다 — `정함 (D-42)`.
- **나가는 조건** — A′(F9 포함)가 PREREG 「후보 기준」의 `K-SC1-5`(ASG 계획 종료)를 넘는다(채택한 행만). 끝내기 수정(F1)의 효과는 SC9의 A vs A′로 보고(D-79), 끝내기 기준 `K-SC9-5`는 A′(M0)와 새 엔진(M3 · M5)에서 본다(채택한 행만). A′만으로 풀린 근거는 분리 근거에서 뺀다([verification.md](verification.md) 「판정과 결과」).

## 선행 조건

`제안` — F1–F9 밖에서, 나누기와 EKS 전에 앱이 갖출 것. 포트와 역할의 단일 집이다.

**포트**

| 포트 | 쓰임 | 누가 닿는가 | 상태 |
|---|---|---|---|
| 8080 | 공개 — REST · SSE · WS (`/api/v1`) | ALB | 운영 중 |
| 8081 | 관리 — `management.server.port`, `/actuator/health/**` · `/actuator/prometheus` | 대상 그룹 검사 · Prometheus | 제안 |
| 8082 | 내부 — 허용 목록 호출만 받는 별도 커넥터(`/internal/**`) | 상대 서비스만. ALB는 내부 경로를 404로 막는다([architecture.md](architecture.md) 「공개 경로」) | 제안 (D-53) |

- 관리 포트로 옮길 때 대상 그룹 검사 경로와 Prometheus 수집 대상을 같이 바꾼다 → [../platform.md](../platform.md) 「관측」 「확인할 운영 값」.

**역할** — 이미지 하나를 env의 역할 값으로 나눈다. 비교군 B0(같은 jar 두 함대)와 EKS E3이 쓴다. 값 이름은 구현 PR에서 정한다.

| 역할 | 켜는 것 | 끄는 것 |
|---|---|---|
| `main` | 공개 REST · SSE, 메인 예약 작업(타이머 · `ScheduledJobRecoveryRunner` · `schedule:job` 리스너), Kafka 컨슈머(`KAFKA_CONSUMER_ENABLED`) | WS 엔드포인트 |
| `session-ws` | WS 엔드포인트, 결과 조회 6-1 · 6-2 | 예약 작업, 컨슈머 |
| `session-worker` | 러닝 예약 작업(`RUNNING_START` · `RUNNING_FORCE_FINISH`), 세션 inbox 컨슈머, 내부 API | WS 엔드포인트, 공개 REST |

**나머지**

| 조건 | 지금 | 언제 |
|---|---|---|
| **드레이너** — `WebSocketDrainer`(`SmartLifecycle`)가 F9의 앱 쪽을 맡는다. `server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase` 명시 | 없다 | M0 |
| **main-api SSE 종료** — 종료 때 열린 SSE(매칭 스트림 · 앱 스트림)를 완료해 앱이 지금 계약대로 다시 붙게 한다. ASG에서는 F9와 함께, 파드 값은 eks 「계획 종료」 main-api 열 | 없다 | M0 |
| **JVM · 컨테이너** — `JAVA_TOOL_OPTIONS`(F8 + `ExitOnOutOfMemoryError` + DNS 캐시 시간), Tomcat keep-alive(값은 [runtime.md](runtime.md)의 연결 시간 제한 사슬), 기반 이미지 digest 고정 | 옵션 없음, `eclipse-temurin:21-jre` 태그 | M0 |
| **서버 쪽 빈틈 감지** — 배치의 첫 `sequence`가 저장된 마지막 + 1보다 크면 `RUNNING_TRACK_UNAVAILABLE`로 다시 보내게 한다. Redis 재시작 손실(AOF everysec)을 계약 변경 없이 덮는다 | 없다 | M0 |
| **인스턴스를 넘는 수정** — 탈퇴 소켓 닫기를 모든 인스턴스로, 늦은 DISCONNECTED(`CloseSupersededSessionHandler`가 닫기 전에 표에서 빼고 나간다 — 범위는 정하지 않음, [runtime.md](runtime.md)), `StartRunningHandler`가 지금 ≥ `start_at`이면 멱등 시작 | `RunningConnectionCloseListener`는 같은 인스턴스만 닫는다 | M0–M3 |
| **끝내기에서 정책 빼기** — 페널티 · 쿨다운 · 평균 페이스를 `RunningFinisher` 밖의 반응으로. 옛 엔진 동결이 지켜지려면 필요하다 | 끝내기 트랜잭션 안 | M0–M2 |
| **끝내기 칸막이** — 동시에 도는 끝내기 수에 상한 | 없다 | M0 |
| **지표** — WS 연결 게이지 · 닫기 코드별 수 · 끝내기 단계 타이머 · Tomcat 스레드. 이름은 [metrics-convention.md](../../metrics-convention.md) 예정 행을 코드보다 먼저 쓴다 | 게이지 없음 | M0 |
| **로그** — k8s 프로필은 stdout ECS, 파일 appender 끔, WS · 컨슈머 경로 MDC | `logging.file.name`이 파일로 쓴다 | M0 (E1) |
| **CI** — `MultiInstanceE2eTest`를 두 번째 인스턴스(`E2E_SECOND_BASE_URL`, 두 번째는 `JPA_DDL=none`)로 돌린다 · 경로 필터 · prod 빌드가 테스트를 돈다 | CI가 `E2E_SECOND_BASE_URL`을 주지 않아 그 테스트가 건너뛴다 · 경로 필터 없음 · `backend-cd-prod.yml`이 `bootJar -x test` | M0 |
| **CI (배포물 둘)** — `E2E_WS_BASE_URL`로 WS를 따로 가리킨다 · 계약 테스트 · Kafka를 낀 서비스 사이 E2E | `E2eTestSupport`가 `E2E_BASE_URL` + `/ws/running`으로 WS 주소를 만든다 | M1–M2 |

## 아웃박스 일반화

`제안` — 받는 쪽 DLT · 건너뜀 알람 · 대조는 `정함 (D-51)`.

- **쓰는 길을 하나로** — `OutboxEventWriter`(종류 · 대상 ID · 토픽 · 본문)를 두고 업무 이름 포트 뒤에 둔다(지금 `RecordCardMusicOutboxAdapter`가 `RequestRecordCardMusicPort` 뒤에 있는 것처럼). UUIDv7 id · 환경 접두어 토픽 · 본문 만들기를 어댑터마다 되풀이하지 않는다. 메인과 세션 둘 다.
- **CHECK를 먼저 넓힌다** — `ck_outbox_event_aggregate_type` · 토픽 CHECK · `AggregateType`을 새 이벤트에 맞게 넓히고, 같은 변경을 손으로 쓴 test · prod DDL에 배포 전에 넣는다 — 빠지면 업무 트랜잭션이 롤백돼 500이다. erd `outbox_events`도 같은 PR에서.
- **메인 쪽 새 이벤트는 커넥터를 바꾸지 않는다** — EventRouter가 `topic` 열로 보낸다(`route.by.field`).
- **러닝 DB는 자기 아웃박스 · 커넥터를 갖는다** — `outbox_events` · `cdc_heartbeats` · 두 번째 슬롯 — [../platform.md](../platform.md) 「러닝 DB CDC」.
- **받는 쪽은 결과 이벤트 전에 막는다** — 지금 inbox는 처리하지 못한 메시지를 ERROR로 남기고 건너뛴다. 결과 이벤트를 받기 전에 DLT(또는 보관 테이블) · 건너뜀 알람 · 대조를 만든다 — [../outbox-inbox.md](../outbox-inbox.md) 「받는 쪽 — Inbox」.
- 이벤트마다 [../outbox-inbox.md](../outbox-inbox.md) 「새 이벤트를 더할 때」를 따른다. 이벤트 후보는 [architecture.md](architecture.md) 「이벤트 (후보)」.

## 라우팅

`정함 (D-78)` — 방마다 엔진을 고르는 라우터는 두지 않는다(D-36을 바꿨다).

- **공개 주소는 경로 규칙으로 나눈다** — dev는 compose의 경로 프록시, 운영은 ALB([architecture.md](architecture.md) 「공개 경로」).
- **메인에 엔진 스위치 하나** — 세션으로 정하면 메인은 확정 때 방 확정 이벤트를 쓰고 옛 엔진의 `RUNNING_START` · `RUNNING_FORCE_FINISH` 예약을 걸지 않는다. 옛 엔진으로 정하면 지금 그대로다. 방마다가 아니라 전체 스위치다.
- **ALB 가중치 대상 그룹은 쓰지 않는다** — 가중치는 연결 단위라 한 방의 참가자가 두 서비스로 갈린다.

## 진행 중 방과 되돌리기

`정함 (D-78)`

1. **전환은 진행 중 방이 0일 때 한다** — 조용한 시간(05:00–06:00 KST)에 진행 중 방 0을 확인하고, 메인 엔진 스위치를 세션으로 돌린 뒤 ALB 경로 규칙을 건다. 옛 엔진의 진행 중 방을 세션으로 옮기지 않는다.
2. **되돌리기도 같은 순서를 거꾸로** — 세션 방이 진행 중이면 경로를 되돌릴 때 그 방이 끊긴다(ALB는 방 번호로 나누지 못한다). 그래서 되돌리기는 조용한 시간에 하고, 슬롯 시간의 결함은 고쳐서 앞으로 간다.
3. **test에서 전환과 되돌리기를 한 번씩 연습한다** — M7 전에.

| # | 되돌리기 | 걸리는 시간 | 쓸 수 있는 때 |
|---|---|---|---|
| 1 | ALB 경로 규칙을 모놀리스로 + 메인 엔진 스위치를 옛 엔진으로 | 분 | M7부터 M8 전까지, 진행 중 세션 방이 0일 때 |

## 병행 유지

`정함 (D-78)` — M1부터 M8까지 옛 엔진(운영)과 새 앱(dev · test)이 함께 있다.

1. **옛 엔진은 M1부터 얼린다** — 데이터 손실 · 장애 · 보안 · 끝내기 폭주 회귀만 고친다. 고치면 같은 PR에서 새 앱에도.
2. **새 실시간 기능은 새 앱에만** — 운영에 나가는 것은 M7 뒤다.
3. **`TrackFilter` · `TrackAnalyzer`는 골든 트랙 동등성 테스트를 지난다** — 익명화한 실제 트랙으로 옛 것과 새 것을 견준다. 한쪽만 바꾸면 같이 바꾸거나 '의도한 차이'로 날짜와 함께 decisions.md에 적는다.
4. **메인 기능은 그대로 간다** — 매칭 · 채팅 · 기록 목록 · 기록 카드는 새 앱이 베끼지 않는다. 결합은 이벤트 계약뿐이고 더하기만 한다.
5. **기간을 정한다** — M1에서 M7까지 목표 기간을 decisions.md에 적고, 넘으면 이유를 적는다.

## 단계 M0–M9

`정함 (D-22, D-78)` — 새로 만들기 → 검증 → 별도 저장소 → 운영 전환 → 지우기의 순서. 단계를 쪼갠 모양은 `제안`, M6을 M7 앞에 두는 것은 `정함 (D-62)`. 진행 상태는 [README.md](README.md) 「진행 단계」. 순서가 바뀌어도 번호는 다시 매기지 않는다.

### M0 기준선 · 선행 수정

옛 엔진만, test와 운영. 겹치는 EKS 단계: E0 · E1.
- [ ] 0단계 운영 증거 (11-25 ASG · Kibana · Grafana까지, SQL 집계 · REST 비율 · CPU 크레딧은 남았다) — [RUNBOOK](../../../load_test/msa/RUNBOOK.md) 「0. 운영 증거」 → [RESULTS](../../../load_test/msa/RESULTS.md) 「0. 운영 증거」
- [ ] 앱 재접속 값을 받아 [runtime.md](runtime.md)에 넣는다
- [ ] 하네스 v2([load_test/msa/README.md](../../../load_test/msa/README.md))와 PREREG 동결 — ack 없는 기록 검사와 불변식 SQL(I1 · I2 · I4)을 「무결성 검사」에 둔다
- [ ] 비교군 A 기준선 — SC0 관문, 그다음 SC1 · SC3 · SC4 · SC9 · SC10
- [x] F1 끝내기 구조 — 판정(잠금) → 봉인(임대: 분석 · S3 · 날씨) → 확정(잠금) (D-79, D-82) — 코드 · 단위 · 통합 테스트 11-25. E2E · 효과(SC9 A vs A′)는 아직
- [ ] F9 계획 종료 (D-65)
- [x] F3 강제 종료 — 알람 확인 + 러너별 워커 + 1분 뒤 다시 걸기 (D-83) — 코드 · 단위 · 통합 테스트 · E2E 11-25
- [x] F5 Hikari 명시 — `pool-name=main` · 나머지는 환경변수(기본값): 최대 `DB_POOL_MAX_SIZE`(10) · 최소 `DB_POOL_MIN_IDLE`(10) · 대기 상한 `DB_POOL_CONNECTION_TIMEOUT_MS`(5000) · 누수 감지 `DB_POOL_LEAK_DETECTION_MS`(10000) (D-66) — E2E 11-25: 누수 경고 0 · 대기 시간 초과 0 · 최장 사용 0.33초
- [ ] F6 · F7 · F8 (D-66) — 하나씩, 파일 단위로
- [x] ~~F4 `app_versions` 30초 캐시~~ — 버림 (D-84)
- [ ] 「선행 조건」의 M0 행
- [ ] 비교군 A′로 다시 잰다 — 'A vs A′' 장

**나가는 조건** — `K-SC0-*` 관문 통과. 0단계 증거와 A 기준선이 RESULTS에 있다. A′(F9 포함)가 PREREG 「후보 기준」의 `K-SC1-5`와 `K-SC9-5`(F1)를 넘는다(채택한 행만). `MultiInstanceE2eTest`가 CI에서 돌고 prod 빌드가 테스트를 돈다. 새 지표가 metrics-convention에서 사용 중이다.

### M1 별도 앱 뼈대 + 루트 compose (D-78)

같은 저장소. 겹치는 EKS 단계: E2.
- [ ] `running-session-server/` — Spring Boot 앱, 자기 `build.gradle` · Dockerfile, base package `com.runiverse.running_session` — 「앱 모양」
- [ ] 루트 `compose.yml` — 메인 8080 · 세션 8090 · Postgres 둘 · Redis 둘 · Kafka + Debezium · 경로 프록시
- [ ] 경로 프록시 설정 — architecture 「공개 경로」와 같은 규칙
- [ ] JWT 검증만 · 핸드셰이크 최소 버전 관문 · 자기 오류 코드와 WS 오류 변환기
- [ ] CI에 새 앱 빌드 · 테스트를 더한다(경로 필터)

**나가는 조건** — compose 하나로 두 앱이 뜨고, 기존 E2E가 프록시 주소로 고치지 않고 통과한다(새 앱은 아직 받는 길이 없다).

### M2 계약 (아웃박스 · CDC · inbox, DLT)

메인 쪽 아웃박스 · inbox와 새 앱 쪽을 함께. dev는 루트 compose의 Kafka + Debezium으로 이벤트가 실제로 오가고(D-78), 운영과 같은 CDC 절차(RDS · 슬롯 · 경보)는 test에서 본다. 겹치는 EKS 단계: E5(데이터).
- [ ] 확인한다 — 앱이 기록 목록에서 6-1 · 6-2를 다시 여는지(「옛 기록」, D-69). 넘기기(D-71) · 사본 모양(D-68)은 정했다
- [ ] 이벤트 · 내부 API 본문을 api-spec 14장에 먼저 쓴다 — 방 확정 · 사용자 변경 · 참가자 결과 (D-71, D-72)
- [ ] 「아웃박스 일반화」 — 쓰는 길, CHECK, 손 DDL
- [ ] 러닝 DB · 두 번째 커넥터 · 슬롯 — platform 「러닝 DB CDC」
- [ ] DLT · 건너뜀 알람 · 대조 작업 (D-51) — consistency 「대조」
- [ ] 계약 픽스처를 생산자 · 소비자 테스트가 함께 쓴다. 같은 이벤트 id를 두 번 받으면 행이 하나다
- [ ] 기본 E2E는 루트 compose의 Kafka + Debezium으로 돈다(D-78), 운영과 같은 CDC 절차는 test에서

**나가는 조건** — 진짜 CDC로 전체 흐름(매칭 → 확정 → 시작 → 끝내기 → 6-1 · 7-1 · 9-2 카드)이 통과한다. CDC 끝-끝 지연 지표가 나온다. 보관 · 건너뜀 0, 대조 누락 0(I5). SC6 1부(모놀리스 비교군)와 2부(지연 탐침 커넥터)를 돌려 `K-SC6-1`이 성립하고 `K-SC6-3`(크기 한도 초과)이 성립하지 않는다 — 2부 지연은 보고만 한다(3부는 B2라 M5).

### M3 새 엔진 + 동등성

새 앱. compose에서 두 앱으로 시험한다. 겹치는 EKS 단계: E3.
- [ ] 시작 — 넘기기 사본을 읽는다. 모르는 방이면 허용 목록 1, 안 되면 `RUNNING_SESSION_UNAVAILABLE`(앱이 다시 보낸다)
- [ ] 위치 · 콤보 — 지금 Redis 로직을 새 키 공간으로
- [ ] 끝내기 — F1 구조(D-63, D-82): 판정(필터 · 목표 판정 · 임대) → 봉인(분석 · S3 원본 · 날씨) → 짧은 확정(기록 · 구간 · 아웃박스) → 커밋 뒤 ack (I1) — [runtime.md](runtime.md). 끝내기 단계 타이머로 `K-SC9-5`를 본다
- [ ] 러닝 DB 예약 작업(행 점유) · 혼자 달리기 청소기 · 밀린 작업 훑기 (D-29)
- [ ] 사용자 삭제 때 모든 인스턴스의 소켓을 세션 Redis 채널로 닫는다
- [ ] 탈퇴 saga — 메인은 정산을 허용 목록 2의 HTTP 클라이언트로 부른다 — consistency 「계정 탈퇴 saga」
- [ ] 골든 트랙 동등성 테스트, E2E를 두 구성(메인 하나 · compose 두 앱)으로, 두 구성 모두 `MultiInstanceE2eTest`

**나가는 조건** — 두 구성의 E2E와 다중 인스턴스 실행 통과. 골든 트랙 차이 0.

### M4 앱 변경 출시 + 최소 버전 (운영 카나리 없음, D-78)

앱 · 운영 ALB. 서버 작업과 나란히 일찍 시작한다(스토어 심사).
- [ ] 확정 뒤 대기 · 이탈 · 카운트다운을 러닝 WS로 받는 앱(D-73)을 iOS · 안드로이드 모두 낸다 — 계약은 M3 전에 api-spec에 먼저 적는다
- [ ] M7 전에 최소 버전을 그 버전으로 올리고 ALB 차단을 건다(D-74)

**나가는 조건** — 두 플랫폼 새 버전이 스토어에 있고, 최소 버전이 올라갔다.

### M5 별도 프로세스 검증 (B2, test)

test만. 겹치는 EKS 단계: E5.
- [ ] 새 앱 이미지를 test 서버 compose에 두 번째 앱으로 올린다(test에는 EKS가 없다, D-75) — 「앱 모양」, 러닝 DB · 세션 Redis 자리는 platform 「test 환경」
- [ ] 시드가 넘기기 이벤트를 내게 한다 — 지금 `load_test/sql/seed.sql`은 `MATCHED` 방을 바로 넣는다
- [ ] 핵심 시나리오 SC1–SC7 — 'A′ vs B0 · B2' 장, 탈퇴 saga(SC7) 포함

**나가는 조건** — `K-SC1-*` ~ `K-SC7-*` 결과가 RESULTS에, 판정이 verification 「판정과 결과」에 있다. 나누는 것은 정했으므로(D-45, D-47) 가부 관문이 아니라 증거다 — 기준을 넘지 못한 근거는 판정 단어를 바꾼다.

### M6 별도 저장소로 옮기기

`정함 (D-62, D-78)` — 별도 저장소는 `정함 (D-47)`.
- [ ] 새 저장소(이름 D-61), `git filter-repo --subdirectory-filter running-session-server`로 이력을 남겨 폴더를 옮긴다
- [ ] 계약 픽스처를 버전 붙은 산출물(GitHub Packages)로 먼저 내고 양쪽 계약 테스트를 건다
- [ ] 서비스별 이미지 · ECR · Parameter Store 경로 · CI(테스트 도는 prod 빌드)
- [ ] 러닝 DB Flyway (D-56)
- [ ] 메인 저장소의 루트 compose는 새 저장소 이미지를 받아 쓴다
- [ ] [README.md](README.md) 「저장소를 나눌 때」 — runtime.md · erd 러닝 DB 장 옮기기, 결정 기록 `RS-D-01`

**나가는 조건** — 새 저장소 CI(단위 · E2E) 통과. 메인 저장소 compose가 새 저장소 이미지로 두 앱 E2E를 통과한다. 양쪽 계약 테스트 통과.

### M7 운영 추출

운영, 새 저장소 배포물. 겹치는 EKS 단계: E5.
- [ ] 선행 — M4(최소 버전 상향) 끝, M5 결과가 RESULTS에, test에서 전환 · 되돌리기를 한 번씩 연습했다
- [ ] 세션 자리 — EKS 워크로드(E5) 또는 자기 ASG · 대상 그룹
- [ ] 전환 — 조용한 시간에 진행 중 방 0을 확인하고, 메인 엔진 스위치를 세션으로, ALB 경로 규칙(architecture 「공개 경로」)과 내부 경로 404를 건다 (「진행 중 방과 되돌리기」)

**나가는 조건** — 2–4주. 슬롯 시간에 양쪽 각각 독립 배포 2번 이상, 서로 영향 0(끊김 · 끝내기 실패). 잃은 러닝 0, 대조 누락 0(I5).

### M8 옛 엔진 삭제

메인 저장소.
- [ ] 옛 엔진 목록의 파일을 지운다
- [ ] 공유 클래스를 줄인다 — `RunningRoom`의 러닝 메서드, `RunningPersistenceAdapter`의 러닝 포트, 끝내기만 쓰던 사용자 포트
- [ ] 메인 엔진 스위치를 지운다(세션만 남는다)

**나가는 조건** — 「지울 기준 A · B」의 A.

### M9 버림 (D-78)

모놀리스 안 호스트가 없어 지울 것이 없다. 번호는 다시 쓰지 않는다.

## 옛 기록

`정함 (D-69)` — 옮기지 않는다.

- **메인에 남는다** — 지금 `running_records` · `running_segments` · `running_splits`는 메인에 그대로 둔다. 평균 페이스 · 7-1 · 기록 카드는 메인을 읽는다(D-68).
- **러닝 DB는 비어서 시작한다** — 새 엔진 방의 기록만 차곡차곡 쌓는다.
- **분리 전 방의 6-1 · 6-2** — 결과는 세션이 응답하고(D-58) ALB 경로 규칙은 방 번호로 나눌 수 없어, 세션은 옛 방을 모른다. 앱이 기록 목록에서 대시보드를 다시 여는 경로가 있으면 M7 전에 옛 방 결과를 러닝 DB로 한 번 복사한다. 그 경로가 있는지는 확인 전이다(「정하지 않은 것」).

## 지울 기준 A · B

`제안 (D-37)` — D-21의 '100% 2주 뒤 삭제'를 바꿨다. 성능 기준 숫자는 PREREG 「후보 기준」.

**A — 옛 엔진 (M8)**. 모두 참이어야 한다.

1. 새로 확정되는 방의 100%가 새 엔진인 채로 연속 4주 이상, 슬롯 저녁 20번 이상(주말 정점 포함), 그 사이 슬롯 시간 세션 배포 1번 이상.
2. SQL로 진행 중 옛 엔진 방 0. 마지막 옛 방이 닫힌 뒤 강제 종료 지평(+6시간)과 트랙 TTL(7시간)이 지났고 오래된 혼자 달리기 `RUNNING` 행이 없다.
3. 그 기간 옛 엔진으로 되돌린 적이 없고, 옛 엔진을 꺼 둔(코드는 남은) 채 1–2주가 지났다.
4. 새 엔진이 M0 기준선 이상이다 — 끝내기 성공률 · CDC 지연 · `RUNNING_TRACK_UNAVAILABLE`과 예상 밖 끊김 비율(PREREG 「후보 기준」), ack 없는 기록 0(I1), 대조 누락 0(I5), 보관 · 건너뛴 inbox 0, 끝낸 뒤 6-1 자기 기록 빠짐 0.
5. 골든 트랙 동등성과 E2E가 SESSION만으로 통과한다(CI의 `MultiInstanceE2eTest` 포함).
6. WS 메시지 · 6-1 · 6-2의 api-spec 차이가 없다. 앱에 보이는 것이 바뀌었으면 먼저 iOS · 안드로이드 최소 버전 상향과 옛 버전 ALB 차단(11-03처럼)을 한다.
7. 지우는 PR이 옛 엔진 목록의 파일만 지우고 공유 클래스를 줄인다 — 목록 참조 검사 0.

**B — 버림 (D-78)**. 모놀리스 안 호스트가 없어 지울 기준도 없다. M7의 나가는 조건이 그 몫을 맡는다.

## 새 저장소가 따르는 규칙

| 규칙 | 상태 |
|---|---|
| 메인 규칙을 그대로 따른다 — 레이어 규칙 ([docs/architecture.md](../../architecture.md))(base package만 자기 것), 호환 깨는 변경 묶기(11-03 1.2.0 + ALB 404, legacy/v1_1은 메인에 남는다), 다중 인스턴스 세 경우(E2E도 두 인스턴스) | 운영 중 (메인 규칙) |
| 러닝 DB 스키마는 Flyway, 메인 운영 스키마는 지금처럼 손으로 쓴 SQL | 정함 (D-56) |
| `WebSocketConfig` → `JwtHandshakeInterceptor` · `RunningWebSocketHandler` 직접 import 예외는 새 저장소에서 다시 정하고, 메인에서는 M8에 뺀다 | 제안 |
| 로그 · 메트릭 · API 표면은 logging-convention · metrics-convention · api-convention을 링크하고 베끼지 않는다 | 제안 |
| 세션 안에서만 뜻이 있는 결정은 새 저장소에 `RS-D-01`부터, 서비스 사이 · 플랫폼 결정은 [../decisions.md](../decisions.md)에 `D-` | 정함 (D-60) |
| 이벤트 계약은 더하기만 하고 버전을 붙인다. 공개 픽스처와 양쪽 계약 테스트 | 정함 (D-47) |

## 단계마다 고칠 문서

운영 문서(kafka · cdc · outbox-inbox · msa README의 지금 절)는 만들고 검증한 PR에서만 고친다. 그때까지 여기서 챙긴다. 모든 단계는 [README.md](README.md) 「진행 단계」를 함께 고친다.

| 단계 | 문서 | 고칠 것 |
|---|---|---|
| M0 | [metrics-convention.md](../../metrics-convention.md) 「기본 제공과 직접 만드는 것」, load_test/msa PREREG · RESULTS, platform 「확인할 운영 값」 | 새 서버 지표 예정 행(코드가 들어가면 사용 중), 운영 Grafana에 HikariCP(활성 · 대기) 패널 추가 — 0단계에서 풀 기준선을 못 뽑았다, PREREG 동결 · 결과, 관리 포트와 종료 값 |
| M0 | [api-spec.md](../../api-spec.md) 0 · 5 · 2-3 | `4500`이 계획 종료에도 온다는 메모(계약 그대로, D-42 · D-65). F4는 버려(D-84) 0 공통 규칙의 '다음 요청부터 바로' 문구는 그대로다 |
| M1 | 레이어 규칙 ([docs/architecture.md](../../architecture.md)) 「패키지 구조」 「의존 방향」, spec-check 스킬 | 별도 앱 폴더 `running-session-server/` · base package · 옛 엔진 목록 규칙, 검사 규칙 |
| M1 | CLAUDE.md | 빌드 · 테스트 명령, 문서 색인 |
| M2 | api-spec 14장 `서비스 사이 계약 — 메인 ↔ 러닝 세션` (새로) | 이벤트 · 내부 API 본문. architecture 「이벤트 (후보)」 「동기 호출 허용 목록」에는 행과 링크만 남긴다 |
| M2–M3 | [erd.md](../../erd.md) | `outbox_events` CHECK 값, 임시 「러닝 DB」 장, 메인 쪽 `running_records` · `running_segments`가 사본이라는 표시(D-68 — FK 대상은 그대로) |
| M2 | [../kafka.md](../kafka.md) 「이름 — 환경 접두어 `{env}`」 「토픽」, [../cdc.md](../cdc.md), [../outbox-inbox.md](../outbox-inbox.md) 「지금 있는 이벤트」 「받는 쪽 — Inbox」, [../README.md](../README.md) 「이벤트 색인」 「구성 — 지금」 | 만들고 검증한 PR에서 — 이름은 platform 「새 이름」에서 옮긴다 |
| ~~날씨 자리를 정할 때~~ | — | **정했다 → D-63** — 세션이 끝낼 때 지금과 같은 기준으로 조회해 값 · 시점이 같다. 고칠 문서 없음 |
| M3 (앱 작업 전) | [api-spec.md](../../api-spec.md) 5-A · 5-B · 5-C · 5-D, 2-3 | 확정 뒤 이탈 요청(형식 정하면), `POST /match-rooms/leave`의 확정된 방 409 코드, 확정 직후 WS 연결 · 대기 멤버 변화 · `RUNNING_READY`를 WS로(매칭 SSE에서 뺌), 최소 버전 — D-73 · D-74. 앱 출시 전에 계약을 먼저 적는다 |
| M5 | verification 「판정과 결과」, load_test/msa RESULTS, rationale 「근거 순위」 | 판정 |
| M6 | [../README.md](../README.md) 「서비스 이름」, [README.md](README.md) 「저장소를 나눌 때」, CLAUDE.md · 루트 README | 실제 이름, runtime.md · erd 러닝 DB 장 옮기기, 문서 색인 |
| M7 (전환 전) | api-spec 7-1 · 2-2 | 7-1은 반영된 뒤 보인다(D-58). `/users/me/status`는 반영 전 `RUNNING`일 수 있다(D-31) |
| M7 | api-spec 6-1 · 6-2, architecture 「공개 경로」, msa README 「구성 — 지금」, platform 「지금 (11-24 기준)」 | 응답 주인 표시(세션), 세션 규칙을 운영 중으로, 새 날짜의 지금 구성 |
| M8 | 레이어 규칙 ([docs/architecture.md](../../architecture.md)), erd 2장 | 옛 엔진 목록 규칙 · WS 직접 import 예외를 지움, 메인에 남는 러닝 테이블만 |
| M8 | 이 문서 | 상태 줄을 기록으로 (M9는 버림, D-78) |

## 정하지 않은 것

1. ~~모놀리스 안 영속성~~ — **정했다 → D-78**: 별도 앱이라 자기 DataSource를 쓰고 메인 영속성 설정과 겹치지 않는다.
2. ~~호스팅 동안 러닝 DB vs 메인 DB 안 스키마~~ — **정했다 → D-78**: 모놀리스 안 호스팅이 없어 처음부터 자기 러닝 DB(dev는 compose의 두 번째 Postgres)를 쓰고, 두 번째 커넥터는 M2에 만든다. test의 러닝 DB 자리는 [../platform.md](../platform.md) 「test 환경」.
3. **Flyway를 들이는 때** — M1 vs 저장소를 나눌 때(M6) (D-56).
4. **힙 `MaxRAMPercentage` 값** — 75 / 70 / 65 (F8). SC11(J1–J4) 결과로 정한다.
5. **분리 전 방의 결과 다시 보기** — 앱이 기록 목록에서 6-1 · 6-2를 다시 여는지 확인한다. 열면 M7 전에 옛 방 결과를 러닝 DB로 복사한다(「옛 기록」, D-69).
6. ~~엔진을 적는 자리~~ — **정했다 → D-78**: 방마다 엔진을 적지 않고 메인의 전체 엔진 스위치 하나다(「라우팅」).
7. **운영에서 F9를 거는 방법** — ASG 인스턴스 새로 고침이 컨테이너에 정상 종료 신호를 주고 기다리게 하는 연결.
8. ~~M6 · M7 순서~~ — **정했다 → D-62**: 저장소를 옮긴 뒤 새 저장소 배포물로 운영 추출한다.
