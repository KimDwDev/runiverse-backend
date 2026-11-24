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
- **이 문서는 메인 저장소에 남는다** — 옛 코드는 메인 저장소에서 지운다. M9 뒤에는 옮긴 기록으로 남는다.

## 방식

`정함 (D-22, D-47)` — 별도 저장소로 옮기는 때(M6을 M7 앞에)는 `제안 (D-62)`.

```text
M0     옛 엔진을 재고 F3–F9를 넣는다                        모놀리스 하나, 지금 배포 그대로
M1–M3  모놀리스 안에 새 엔진을 만든다                 새 엔진 트래픽 0%, 두 엔진 E2E
M4     운영에서 방마다 새 엔진으로 올린다              되돌리기 = 새 방 비율 0%
M5     따로 띄워 test에서 잰다 (B2)
M6     새 엔진을 별도 저장소로 옮긴다                  모놀리스는 그 배포물을 계속 담는다
M7     운영 ALB 경로를 새 저장소 배포물로 돌린다       되돌리기 = 경로 규칙
M8     옛 엔진을 지운다 (지울 기준 A)
M9     모놀리스 안 호스트를 지운다 (지울 기준 B)
```

- **지우지 않고 옆에 새로 만든다** — 지금 러닝 세션 코드(이하 옛 엔진)와 기능은 그대로 둔다. 새 엔진은 처음부터 따로 띄울 모양으로 모놀리스 안에 만든다. legacy/v1_1을 남기고 폴더째 지울 수 있게 지킨 것과 같은 결이다.
- **애그리거트를 쪼개지 않고 새로 만든다** — 매칭과 러닝은 한 애그리거트다(「지금 얽힌 곳 (11-24 기준)」). 방 · 매칭 코드는 매주 바뀌고(방 채팅 11-10~17, 초대 · 탈퇴 10-27~11-02, 7-1 카드 요약 11-23), 실시간 러닝 경로의 마지막 기능 변경은 10-20(`dd36727a`)이라 새로 만들 대상이 작고 멈춰 있다 — [rationale.md](rationale.md) 「받쳐 주는 근거 G5–G8」.
- **검증된 운영 길을 그대로 쓴다** — 새 엔진이 증명될 때까지(M4) ALB · ASG · Parameter Store · E2E · 대시보드를 바꾸지 않는다.
- **모놀리스가 되돌릴 곳이다** — 옛 엔진은 M8까지, 모놀리스 안 호스트는 M9까지 남는다(「진행 중 방과 되돌리기」).
- **끝내기 구조는 새 엔진에서 짠다** — `정함 (D-63)`. 옛 엔진의 끝내기는 고치지 않는다. 그래서 끝내기 폭주는 속도 비교에 쓰지 않고 격리로 잰다(D-64). F3 · F5–F9는 모놀리스에 먼저 넣는다 — `정함 (D-65, D-66)`. F2는 새 엔진에서, F4도 모놀리스에(D-67).

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
| 검사 | `settings.gradle`은 루트 프로젝트 하나. `check_conventions.py`는 레이어 방향 · legacy 격리 등을 보고 모듈 경계는 보지 않으며 CI에서 돌지 않는다. ArchUnit · Modulith · Flyway 없음 | 「모듈 모양」 「경계 검사」 |

**메인이 러닝 테이블을 읽는 곳** — 모두 M4(새 엔진 방의 기록이 러닝 쪽에만 생기는 때) 전에 사본이나 허용 목록 호출로 바꾼다. 사본 모양은 정하지 않음([architecture.md](architecture.md) 「메인 사본과 반영」).

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

## 모듈 모양

`제안 (D-35)`

```text
running-service/                     메인 저장소 — 지금은 Gradle 프로젝트 하나
├── settings.gradle                  include 'running-session'
├── build.gradle                     호스팅하는 동안 implementation project(':running-session')
├── src/main/java/com/runiverse/running_service/
│   └── …                            메인 + 옛 엔진 + 호스트(WS 라우터 · 6-1 · 6-2 분기 · 허용 목록 클라이언트)
└── running-session/                 java-library jar — 루트에 의존하지 않는다
    └── src/main/java/com/runiverse/running_session/
        ├── api/                     RunningEngine(시작 · 위치 · 일시정지 · 재개 · 끝내기 · 끊김) · 결과 조회 · 정산
        └── presentation · application · domain · infrastructure
```

- **모듈 → 메인 import는 컴파일이 막는다** — 모듈이 루트 프로젝트에 의존하지 않으므로 `com.runiverse.running_service`를 import하면 빌드가 깨진다. 반대 방향은 「경계 검사」 2. 모듈은 일반 jar를 만든다 — 루트의 `tasks.named('jar') { enabled = false }`는 루트에만 걸린다.
- **메인 스캔 밖이다** — base package가 달라 `RunningServiceApplication`의 `@SpringBootApplication` 스캔과 메인 persistence unit에 들어가지 않는다. 모놀리스는 `runiverse.running-session.enabled` 뒤에서 `@Import`로 켜고, 같은 값이 끄는 스위치다.
- **따로 띄울 호스트는 M5에 더한다** — `running-session-app` 서브프로젝트(Boot main · bootJar · Dockerfile · JWT 검증만 · 핸드셰이크 최소 버전 관문 · `@RestControllerAdvice`). 추출은 코드를 옮기는 일이 아니라 호스트를 더하는 일이다. 이름은 `제안 (D-61)`.

## 원격처럼

`제안` — 모놀리스 안에 있어도 따로 띄운 것처럼 만든다. 그래야 M5 · M7에서 호스트만 바뀐다.

| 무엇 | 모듈이 갖는 것 |
|---|---|
| DB | 자기 스키마 · DB 역할(`public` 권한 없음) 또는 러닝 DB — 「정하지 않은 것」 2 |
| Redis | 자기 키 접두어 · 채널 · 리스너 컨테이너와 연결 설정. 세션 Redis(D-49)가 생기면 설정만 바꾼다 — 옮기는 창은 [../platform.md](../platform.md) 「Redis」 |
| 오류 | 자기 오류 코드 + api-spec의 코드 문자열을 그대로 내는 WS 오류 변환기 |
| 메인과 말하기 | 아웃박스 · inbox와 허용 목록 호출([architecture.md](architecture.md) 「동기 호출 허용 목록」)뿐. 메인 빈 · 포트를 부르지 않는다 |
| 사용자 데이터 | 넘기기 스냅샷 + 프로필 투영 — 사용자 테이블을 읽지 않는다 |
| 예약 작업 | 자기 `scheduled_jobs` · 채널, DB 행 점유로 한 번만 실행 (D-29) |

**모놀리스 안 호스팅의 위험**

- **빈 · 엔티티 이름이 겹친다** — base package가 달라도 같은 빈 이름이나 엔티티 이름 · 테이블이 겹치면 기동이 깨진다. 모듈 클래스 · 테이블은 옛 엔진과 겹치지 않는 이름을 쓴다. 로컬은 `JPA_DDL=update`라 모듈 테이블이 메인 DB에 생긴다.
- **메인 자동 설정이 물러난다** — 두 번째 `DataSource` · `EntityManagerFactory` · `TransactionManager` · `RedisConnectionFactory` 빈이 생기면 Boot가 메인용 자동 설정을 만들지 않는다(Boot 4.1에서 확인한다). 단위 테스트는 컨텍스트를 띄우지 않아 E2E만 잡는다. 고르는 법은 「정하지 않은 것」 1.
- **컨테이너 주입이 모호해진다** — `runningChannelContainer`를 이름으로 받는 곳(「지금 얽힌 곳 (11-24 기준)」). 모듈 컨테이너를 빈으로 내지 않거나 기존 빈에 `@Primary`를 단다.
- **한 인스턴스에 JVM 둘** — M5에서 t3.small 하나에 컨테이너 둘을 띄우면 기본 힙으로는 여유가 적다 → F8.

## 경계 검사

`정함 (D-54)` — 규칙 목록은 `제안`. [check_conventions.py](../../../.claude/skills/spec-check/scripts/check_conventions.py)에 `check_legacy_isolation`과 같은 방식으로 더하고, M1부터 CI에서 PR마다 돌린다. 나가는 조건은 위반 0.

| # | 규칙 | 막는 것 |
|---|---|---|
| 1 | 모듈 → 메인 import 금지 — Gradle 의존 방향이 막는다 | 추출이 다시 쓰기가 되는 것 |
| 2 | 메인 → 모듈은 `com.runiverse.running_session.api`만, 호스트 패키지(WS 라우터 · 6-1 · 6-2 분기 · 허용 목록 클라이언트)에서만 | 메인이 모듈 안쪽에 기대는 것 |
| 3 | 옛 엔진 목록(검사가 읽는 파일)의 클래스를 새 파일이 import하면 위반 | 옛 엔진을 지울 수 없게 되는 것 |
| 4 | 옛 엔진 목록의 파일을 고친 PR은 `mirrored` 라벨이나 `running-session/` 쪽 변경이 있어야 한다. PR 템플릿에 체크 상자를 더한다 | 한 엔진만 고치는 것(「병행 유지」) |
| 5 | JPQL · SQL 문자열 안의 엔티티 · 테이블 이름도 본다 — 다른 쪽 엔티티를 조인하면 위반 | `loadRanTogether`처럼 import를 피한 결합 |
| 6 | 서비스 사이 동기 호출 클라이언트는 허용 목록에 있는 것만 | 목록 밖 호출 (D-52) |

## 선행 수정 F1–F9

F1은 `정함 (D-63)` — 새 엔진(M3)에서만 한다. M0에서 모놀리스에 먼저 넣는 것은 F3 · F5–F9 — `정함 (D-65, D-66)`. F2는 새 엔진에서(D-66), F4도 모놀리스에(D-67). 모놀리스에 넣은 묶음이 비교군 A′다([verification.md](verification.md) 「비교군」). 앱은 바꾸지 않는다 — 다시 붙을 때 전체 트랙 대신 `nextSequence`부터 보내기는 앱 동작이 바뀌는 일이라 넣지 않는다.

| F | 고칠 것 | 지금 코드 |
|---|---|---|
| F1 | **M3 새 엔진에서만 (D-63)** — 끝내기를 봉인과 확정으로 나눈다. 봉인(잠금 · 트랜잭션 밖): Redis 트랙 읽기 · `TrackFilter` · `TrackAnalyzer` · S3 원본 업로드 · 날씨 조회(실패하면 기본값). 확정: 잠금 → 다시 확인 → 기록 · 구간 · 상태 · 아웃박스를 쓰는 짧은 트랜잭션. 옛 엔진은 그대로 둔다 | `RunningFinisher.confirm`이 잠금을 쥔 채 `createRecord`에서 S3 업로드 · 날씨 조회를 기다린다(코드의 TODO) |
| F2 | **M3 새 엔진에서 (D-66)** — 목표 도달 사전 검사 — 필터 거리를 트랜잭션 밖에서 먼저 보고, 닿았을 때만 확정 트랜잭션을 연다 | `UpdateRunningFinishJudge` → `RunningFinisher.finishOnGoal`이 목표를 넘긴 배치마다 `FOR UPDATE` 트랜잭션을 연다 |
| F3 | **M0 모놀리스 (D-66)** — 강제 종료를 러너마다 짧은 트랜잭션 하나로 | `ForceFinishRunningRoomHandler`가 방 전체를 트랜잭션 하나로 닫는다 |
| F4 | **M0 모놀리스 (D-67)** — `app_versions` 30초 캐시. 대가: 최소 버전 상향이 최대 30초 늦게 걸린다 | `AppVersionFilter`가 WS 핸드셰이크를 포함한 요청마다 캐시 없이 읽는다 |
| F5 | **M0 모놀리스 (D-66)** — Hikari를 명시한다 — 풀 크기(비교군 사이 DB 연결 총량을 같게), `connectionTimeout`은 앱의 끝내기 대기보다 짧게, 누수 감지 | `reWriteBatchedInserts`만 설정돼 있다 |
| F6 | **M0 모놀리스 (D-66)** — liveness = `livenessState`, readiness = `readinessState`(db · redis 뺌), 대상 그룹 검사는 readiness | 기본 health 집계에 db · redis가 들고, `SecurityConfig`는 `/actuator/health` · `/actuator/prometheus` 정확한 경로만 인증 없이 연다(ALB 규칙이 health만 통과시킨다) — `/actuator/health/liveness` · `/readiness` 그룹 경로는 열려 있지 않다 |
| F7 | **M0 모놀리스 (D-66)** — `running:room:*` 전용 리스너 컨테이너와 상한 있는 실행기 | `RunningChannelConfig`에 실행기 지정이 없다 |
| F8 | **M0 모놀리스 (D-66)** — `JAVA_TOOL_OPTIONS`로 `MaxRAMPercentage` — 값은 「정하지 않은 것」 4 | Dockerfile `ENTRYPOINT`에 JVM 옵션이 없다 |
| F9 | **M0 모놀리스에 먼저 (D-65)** — WS 계획 종료(ASG) — readiness 내림 → 새 `RUNNING_START` 거절 → SSE 완료 → 소켓을 `4500`으로 나눠 닫기(시간은 [runtime.md](runtime.md)) → graceful 종료. ASG 쪽 값: `docker stop -t 60`, 대상 그룹 등록 해제 지연 60초 | `server.shutdown` 설정도, 소켓을 닫는 종료 코드도 없다 |

- **앱 쪽 순서와 시간**은 [runtime.md](runtime.md), EKS 파드 값은 [../eks.md](../eks.md) 「계획 종료」. 닫기 코드는 `4500`을 다시 쓴다 — `제안 (D-42)`.
- **나가는 조건** — A′(F9 포함)가 PREREG 「후보 기준」의 `K-SC1-5`(ASG 계획 종료)를 넘는다(채택한 행만). 끝내기 기준 `K-SC9-5`는 새 엔진(M3 · M5)에서 본다. A′만으로 풀린 근거는 분리 근거에서 뺀다([verification.md](verification.md) 「판정과 결과」).

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
- **러닝 DB는 자기 아웃박스 · 커넥터를 갖는다** — `outbox_events` · `cdc_heartbeats` · 두 번째 슬롯 — [../platform.md](../platform.md) 「러닝 DB CDC」. 호스팅 동안 모듈이 메인 DB 안 스키마를 쓰면 publication `ADD TABLE`과 `table.include.list` 변경으로 슬롯 하나에 싣는다(「정하지 않은 것」 2).
- **받는 쪽은 결과 이벤트 전에 막는다** — 지금 inbox는 처리하지 못한 메시지를 ERROR로 남기고 건너뛴다. 결과 이벤트를 받기 전에 DLT(또는 보관 테이블) · 건너뜀 알람 · 대조를 만든다 — [../outbox-inbox.md](../outbox-inbox.md) 「받는 쪽 — Inbox」.
- 이벤트마다 [../outbox-inbox.md](../outbox-inbox.md) 「새 이벤트를 더할 때」를 따른다. 이벤트 후보는 [architecture.md](architecture.md) 「이벤트 (후보)」.

## 라우팅

`제안 (D-36)` — D-21의 ALB 가중치 카나리를 바꿨다.

- **공개 주소는 그대로다** — `/api/v1/ws/running`(`websocket.running-endpoint=ws/running`, context path `/api/v1`). 앱 변경도 최소 버전 상향도 없다.
- **얇은 `RunningEngineRouter`가 등록된 WS 핸들러가 된다** — `HEALTH_CHECK`는 직접 답한다. `RUNNING_START`는 `runningRoomId`를 싣고 오므로(`RunningStartRequest`) 그 방의 엔진을 찾아 소켓 속성에 적고 넘긴다. 뒤따르는 프레임과 연결 종료는 그 엔진으로 간다. 6-1 · 6-2도 같은 방 엔진으로 나눈다.
- **엔진은 명단이 정해질 때 고르고 바꾸지 않는다** — 매칭 모집 마감 · 초대 방 시작 직전 · 혼자 달리기 생성 때. 방을 만들 때가 아니다(매칭 방은 며칠 앞서 생긴다). 연결마다도 아니다. 적는 자리는 「정하지 않은 것」 6.
- **ALB 가중치 대상 그룹은 쓰지 않는다** — 가중치는 연결 단위라 한 방의 참가자가 두 엔진으로 갈린다: 서로의 진행률 · 콤보를 놓치고, 마지막 러너가 끝나도 방이 닫히지 않고, 6-1이 반쪽이 된다.
- E4의 Route 53 가중치 이동([../eks.md](../eks.md))은 같은 이미지를 옮기는 일이라 해당하지 않는다 — 양쪽이 같은 엔진 선택 · 같은 Redis를 쓴다. M7의 경로 전환도 엔진이 아니라 호스트를 바꾼다.

## 진행 중 방과 되돌리기

`제안`

1. **비율 · 스위치는 새로 확정되는 방에만 걸린다** — 진행 중 방은 자기 엔진에서 끝난다. Redis 상태를 키 공간 사이로 옮기지 않는다.
2. **모듈은 어떤 되돌리기에도 떠 있다** — 새 엔진 방이 진행 중일 수 있다. 심한 결함이면 모듈이 자기 방을 강제 종료하고, 옛 엔진에 넘기지 않는다.
3. **운영 추출(M7) 전에 옛 엔진을 비운다** — 추출한 서비스는 새 엔진 방만 안다. 진행 중 옛 방이 0이 된 뒤 강제 종료 지평(시작 + 6시간, `match.force-finish-offset`)과 트랙 TTL 7시간(`running-track.ttl`)이 지나고, 오래된 혼자 달리기 `RUNNING` 행이 없어야 한다 — 혼자 달리기에는 강제 종료가 없다(`RunningActivityReader` 주석).
4. **추출 전환은 러닝 중에도 된다** — 두 호스트가 같은 모듈 · 러닝 DB · Redis · 토픽 · 컨슈머 그룹을 쓴다. 옛 소켓은 다시 붙을 때까지 옛 대상에 남고, 다시 붙으면 새 호스트로 가서 앱이 다시 보내고 서버가 중복을 거른다. 강제 종료는 DB 행 점유로 한 번만 돈다. 파티션이 하나라 그룹에서 한 컨슈머만 받는다.

**되돌리기 사다리** — 위에서부터 쓴다. M7 전에 test에서 한 번 연습한다.

| # | 되돌리기 | 걸리는 시간 | 쓸 수 있는 때 |
|---|---|---|---|
| 1 | ALB 경로 규칙을 모놀리스로 되돌린다 — 모놀리스 안 호스트가 같은 데이터로 받는다 | 분 | M7부터 M9 전까지 |
| 2 | 새로 확정되는 방을 옛 엔진으로 — 비율 0%, 진행 중 방은 그대로 | 설정 하나 | M4부터 M8 전까지, ALB가 모놀리스를 가리킬 때 |

## 병행 유지

`제안` — M1부터 M8까지 두 엔진이 함께 있다.

1. **옛 엔진은 M1부터 얼린다** — 데이터 손실 · 장애 · 보안 · 끝내기 폭주 회귀만 고친다. 고치면 같은 PR에서 두 엔진 모두, 테스트도 양쪽에.
2. **새 실시간 기능은 새 엔진에만** — 모든 방에 가야 하면 100%를 기다린다. 앱에 보이는 WS · 6-1 · 6-2 계약 변경은 옛 엔진을 지운 뒤에 한다 — 두 엔진이 같은 api-spec 계약과 오류 코드 문자열을 지켜야 한다.
3. **`TrackFilter` · `TrackAnalyzer`는 골든 트랙 동등성 테스트를 지난다** — 익명화한 실제 트랙으로 옛 것과 새 것을 견준다. 한쪽만 바꾸면 같이 바꾸거나 '의도한 차이'로 날짜와 함께 decisions.md에 적는다 — 아니면 엔진마다 거리 · 칼로리가 달라진다(고도 · 구간 경계 변경 10-16~10-19가 그런 변경이다).
4. **메인 기능은 그대로 간다** — 매칭 · 채팅 · 기록 목록 · 기록 카드는 모듈이 베끼지 않는다. 결합은 이벤트 계약뿐이고 더하기만 한다(넓힌 뒤 좁히기). 픽스처를 같은 PR에서 고친다. 옛 엔진 쪽을 고친 PR은 CI가 잡는다(「경계 검사」 3 · 4).
5. **기간을 정한다** — 첫 카나리에서 100%까지 6–8주를 목표로 한다. 넘는 주마다 이유를 decisions.md에 적는다.
6. **엔진마다 주인 한 명** — 리뷰가 어느 쪽이 정본인지 안다.

## 단계 M0–M9

`정함 (D-22)` — 새로 만들기 → 검증 → 별도 저장소 → 지우기의 순서. 단계를 쪼갠 모양은 `제안`, M6을 M7 앞에 두는 것은 `제안 (D-62)`. 진행 상태는 [README.md](README.md) 「진행 단계」. 순서가 바뀌어도 번호는 다시 매기지 않는다.

### M0 기준선 · 선행 수정

옛 엔진만, test와 운영. 겹치는 EKS 단계: E0 · E1.
- [ ] 0단계 운영 증거 — [RUNBOOK](../../../load_test/msa/RUNBOOK.md) 「0. 운영 증거」 → [RESULTS](../../../load_test/msa/RESULTS.md) 「0. 운영 증거」
- [ ] 앱 재접속 값을 받아 [runtime.md](runtime.md)에 넣는다
- [ ] 하네스 v2([load_test/msa/README.md](../../../load_test/msa/README.md))와 PREREG 동결 — ack 없는 기록 검사와 불변식 SQL(I1 · I2 · I4)을 「무결성 검사」에 둔다
- [ ] 비교군 A 기준선 — SC0 관문, 그다음 SC1 · SC3 · SC4 · SC9 · SC10
- [ ] F9 계획 종료 (D-65)
- [ ] F3 · F5 · F6 · F7 · F8 (D-66) — 하나씩, 파일 단위로
- [ ] F4 `app_versions` 30초 캐시 (D-67)
- [ ] 「선행 조건」의 M0 행
- [ ] 비교군 A′로 다시 잰다 — 'A vs A′' 장

**나가는 조건** — `K-SC0-*` 관문 통과. 0단계 증거와 A 기준선이 RESULTS에 있다. A′(F9 포함)가 PREREG 「후보 기준」의 `K-SC1-5`를 넘는다(채택한 행만). `MultiInstanceE2eTest`가 CI에서 돌고 prod 빌드가 테스트를 돈다. 새 지표가 metrics-convention에서 사용 중이다.

### M1 모듈 뼈대 (트래픽 0%)

모놀리스 안. 겹치는 EKS 단계: E2.
- [ ] `settings.gradle`에 `running-session`, 모듈 `build.gradle`(`java-library`) — 「모듈 모양」
- [ ] `api` — `RunningEngine` · 결과 조회 · 정산
- [ ] 자기 DataSource · DB 역할 · Redis 접두어 · 리스너 컨테이너 · 오류 코드 · WS 오류 변환기 — 「원격처럼」
- [ ] 호스트 — `RunningEngineRouter`(모든 방을 옛 엔진으로), 6-1 · 6-2 분기, `@Import` + `runiverse.running-session.enabled`
- [ ] 경계 검사 1–6과 옛 엔진 목록을 CI에
- [ ] 테스트 — 세션 DB 역할이 `public.users`를 읽지 못한다, 모듈의 모든 Redis 키 · 채널이 접두어로 시작한다

**나가는 조건** — 모듈을 켠 채(0%) 기존 단위 · E2E 테스트가 고치지 않고 통과한다. 모듈을 끈 기동은 지금과 같다. 경계 검사 위반 0. api-spec 차이 없음.

### M2 계약 (아웃박스 · CDC · inbox, DLT)

모놀리스 안, test 먼저. 겹치는 EKS 단계: E5(데이터).
- [ ] 먼저 정한다 — 넘기기 방식(architecture 「넘기기」), 메인 사본 모양(architecture 「메인 사본과 반영」), 「옛 기록」
- [ ] 이벤트 · 내부 API 본문을 api-spec 14장에 먼저 쓴다 — 넘기기 · 결과 셋 · 사용자 생애 (D-28)
- [ ] 「아웃박스 일반화」 — 쓰는 길, CHECK, 손 DDL
- [ ] 러닝 DB(또는 스키마) · 두 번째 커넥터 · 슬롯 — platform 「러닝 DB CDC」
- [ ] DLT · 건너뜀 알람 · 대조 작업 (D-51) — consistency 「대조」
- [ ] 계약 픽스처를 생산자 · 소비자 테스트가 함께 쓴다. 같은 이벤트 id를 두 번 받으면 행이 하나다
- [ ] 기본 E2E는 로컬 중계(로컬 가짜 AI 완료 D-13처럼), 진짜 CDC는 test에서

**나가는 조건** — 진짜 CDC로 전체 흐름(매칭 → 확정 → 시작 → 끝내기 → 6-1 · 7-1 · 9-2 카드)이 통과한다. CDC 끝-끝 지연 지표가 나온다. 보관 · 건너뜀 0, 대조 누락 0(I5). SC6 1부 · 2부를 모놀리스 안 호스팅으로 돌려 `K-SC6-1`이 성립하고 `K-SC6-3`(크기 한도 초과)이 성립하지 않는다 — 2부 지연은 보고만 한다(3부는 B2라 M5).

### M3 새 엔진 + 동등성

모놀리스 안, 운영은 그림자만. 겹치는 EKS 단계: E3.
- [ ] 시작 — 넘기기 사본을 읽는다. 모르는 방이면 허용 목록 1, 안 되면 `RUNNING_SESSION_UNAVAILABLE`(앱이 다시 보낸다)
- [ ] 위치 · 콤보 — 지금 Redis 로직을 새 키 공간으로
- [ ] F2 목표 도달 사전 검사를 트랜잭션 밖으로 (D-66)
- [ ] 끝내기 — F1 구조(D-63): 잠금 밖 S3 원본 · 날씨 → 짧은 확정(기록 · 구간 · 아웃박스) → 커밋 뒤 ack (I1) — [runtime.md](runtime.md). 끝내기 단계 타이머로 `K-SC9-5`를 본다
- [ ] 러닝 DB 예약 작업(행 점유) · 혼자 달리기 청소기 · 밀린 작업 훑기 (D-29)
- [ ] 사용자 삭제 때 모든 인스턴스의 소켓을 모듈 채널로 닫는다
- [ ] 탈퇴 saga — 메인은 정산을 허용 목록 2의 클라이언트로 부른다. 호스팅하는 동안에도 같은 인터페이스다 — consistency 「계정 탈퇴 saga」
- [ ] 그림자 분석 — 옛 엔진 끝내기마다 새 분석기를 읽기 전용으로 돌려 거리 · 시간 · 칼로리 차이를 지표로 낸다
- [ ] 골든 트랙 동등성 테스트, E2E 두 번(`E2E_RUNNING_ENGINE=LEGACY` · `SESSION`), 두 엔진 모두 `MultiInstanceE2eTest`

**나가는 조건** — 두 E2E와 두 다중 인스턴스 실행 통과. 골든 트랙 차이 0. 운영 그림자 차이가 1주 이상 0이거나 decisions.md에 설명이 있다.

### M4 방 단위 카나리 (모놀리스 안)

운영 모놀리스 안. 겹치는 EKS 단계: E4.
- [ ] 새 엔진 비율 — 내부 계정 → 5% → 25% → 50% → 100%, 단계마다 슬롯 저녁 3번 이상
- [ ] 엔진별 대시보드 — 끝내기 성공률, ack 없는 기록, 끝낸 뒤 6-1 자기 기록 빠짐, `RUNNING_TRACK_UNAVAILABLE`, 예상 밖 끊김, CDC 지연, 보관 이벤트
- [ ] 멈추는 규칙을 decisions.md에 적는다

**나가는 조건 (비율 단계마다)** — 끝내기 성공률이 PREREG 「후보 기준」 이상이고 옛 엔진보다 낮지 않다. ack 없는 기록 0(I1), 보관 0, 6-1 자기 기록 빠짐 0, 대조 누락 0(I5), 되돌리기 없음.

### M5 별도 프로세스 검증 (B2, test)

test만. 겹치는 EKS 단계: E5.
- [ ] `running-session-app` 서브프로젝트 — 「모듈 모양」
- [ ] test에 두 번째 컨테이너(또는 EKS 워크로드)로 띄운다 — 러닝 DB · 세션 Redis는 platform 「test 환경」
- [ ] 시드가 넘기기 이벤트를 내게 한다 — 지금 `load_test/sql/seed.sql`은 `MATCHED` 방을 바로 넣는다
- [ ] 핵심 시나리오 SC1–SC7 — 'A′ vs B0 · B2' 장, 탈퇴 saga(SC7) 포함

**나가는 조건** — `K-SC1-*` ~ `K-SC7-*` 결과가 RESULTS에, 판정이 verification 「판정과 결과」에 있다. 나누는 것은 정했으므로(D-45, D-47) 가부 관문이 아니라 증거다 — 기준을 넘지 못한 근거는 판정 단어를 바꾼다.

### M6 별도 저장소로 옮기기

`제안 (D-62)` — 별도 저장소는 `정함 (D-47)`.
- [ ] 새 저장소(이름 D-61), `git filter-repo --subdirectory-filter`로 모듈 이력을 남긴다
- [ ] 계약 픽스처를 버전 붙은 산출물(GitHub Packages)로 먼저 내고 양쪽 계약 테스트를 건다
- [ ] 공유 코드(폴리라인 · JWT 검증)는 공개하거나 동등성 테스트 아래 복제한다
- [ ] 서비스별 이미지 · ECR · Parameter Store 경로 · CI(경로 필터 · E2E · 테스트 도는 prod 빌드)
- [ ] 러닝 DB Flyway(M1에 들이지 않았으면) (D-56)
- [ ] 모놀리스는 새 저장소가 낸 모듈 버전을 받아 계속 담는다 — 되돌릴 곳
- [ ] [README.md](README.md) 「저장소를 나눌 때」 — runtime.md · erd 러닝 DB 장 옮기기, 결정 기록 `RS-D-01`

**나가는 조건** — 새 저장소 CI(단위 · E2E · 경계 검사 위반 0) 통과. 모놀리스가 공개된 모듈 버전으로 두 엔진 E2E를 통과한다. 양쪽 계약 테스트 통과.

### M7 운영 추출

운영, 새 저장소 배포물. 겹치는 EKS 단계: E5.
- [ ] 선행 — M4 100%, 옛 엔진을 비웠다(「진행 중 방과 되돌리기」 3), 옛 엔진 스위치를 껐다(코드는 남는다)
- [ ] 세션 자리 — 자기 ASG · 대상 그룹 또는 같은 호스트의 두 번째 컨테이너(t3.small 메모리 확인), EKS면 E5
- [ ] ALB 규칙 — architecture 「공개 경로」의 세션 규칙과 내부 경로 404를 조용한 시간에 건다
- [ ] 모놀리스는 같은 러닝 DB · Redis 접두어 · 컨슈머 그룹으로 모듈을 계속 담는다
- [ ] 되돌리기 전환을 test에서 먼저 연습한다

**나가는 조건** — 100%로 2–4주. 슬롯 시간에 양쪽 각각 독립 배포 2번 이상, 서로 영향 0(끊김 · 끝내기 실패). 운영에서 되돌리기 전환을 한 번 하고 잃은 러닝 0.

### M8 옛 엔진 삭제

메인 저장소.
- [ ] 옛 엔진 목록의 파일을 지운다
- [ ] 공유 클래스를 줄인다 — `RunningRoom`의 러닝 메서드, `RunningPersistenceAdapter`의 러닝 포트, 끝내기만 쓰던 사용자 포트
- [ ] 라우터의 옛 엔진 분기를 지운다

**나가는 조건** — 「지울 기준 A · B」의 A.

### M9 모놀리스 안 호스트 제거

메인 저장소. E7은 이 뒤.
- [ ] 운영에서 `runiverse.running-session.enabled=false`로 1–2주
- [ ] 메인의 모듈 의존 · 라우터 · 허용 목록 클라이언트의 모놀리스 안 구현을 지운다
- [ ] 이 문서의 상태 줄을 '기록'으로 바꾼다

**나가는 조건** — 「지울 기준 A · B」의 B. 루트 프로젝트가 모듈에 의존하지 않는다.

## 옛 기록

`정하지 않음` — M2에서 메인 사본 모양과 함께 정한다.

- (가) **옮기지 않는다** — 지금 `running_records` · `running_segments` · `running_splits`는 메인에 남는다. 러닝 DB는 비어서 시작해 새 엔진 방만 갖는다. 평균 페이스 · 7-1 · 기록 카드는 메인을 읽는다. 메인이 영구 사본이라 러닝 DB는 나중에 보존 기간으로 줄일 수 있다.
- (나) **논리 복제로 옮긴다** — 러닝 테이블을 러닝 DB로 복제하고 조용한 시간(05:00–06:00 KST, 활성 방 0을 확인한 때)에 끊는다.
- **분리 전 방의 6-1 · 6-2** — 결과는 세션이 응답하므로(D-58) (가)면 세션에 옛 방이 없다. ALB 경로 규칙은 방 번호로 나눌 수 없다. 후보: 옛 방 결과만 러닝 DB로 한 번 복사 / 허용 목록에 옛 결과 당겨 오기를 더한다.

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

**B — 모놀리스 안 호스트 (M9)**. 모두 참이어야 한다.

1. 추출한 서비스가 `/api/v1/ws/running`의 100%를 2–4주 이상 받았다.
2. 슬롯 시간에 양쪽 각각 독립 배포 2번 이상, 서로 영향 0.
3. 되돌리기 전환을 한 번 연습했다.
4. 호스트를 설정으로 끈 채 1–2주, 트래픽 0.

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
| M0 | [metrics-convention.md](../../metrics-convention.md) 「기본 제공과 직접 만드는 것」, load_test/msa PREREG · RESULTS, platform 「확인할 운영 값」 | 새 서버 지표 예정 행(코드가 들어가면 사용 중), PREREG 동결 · 결과, 관리 포트와 종료 값 |
| M0 | [api-spec.md](../../api-spec.md) 5 · 2-3 | `4500`이 계획 종료에도 온다는 메모(계약 그대로, D-42). F4를 승인하면 최소 버전이 최대 30초 늦게 걸린다는 메모 |
| M1 | 레이어 규칙 ([docs/architecture.md](../../architecture.md)) 「패키지 구조」 「의존 방향」, spec-check 스킬 | 서브프로젝트 · base package · 옛 엔진 목록 규칙, 검사 규칙 |
| M1 | CLAUDE.md | 빌드 · 테스트 명령, 문서 색인 |
| M2 | api-spec 14장 `서비스 사이 계약 — 메인 ↔ 러닝 세션` (새로) | 이벤트 · 내부 API 본문. architecture 「이벤트 (후보)」 「동기 호출 허용 목록」에는 행과 링크만 남긴다 |
| M2–M3 | [erd.md](../../erd.md) | `outbox_events` CHECK 값, 임시 「러닝 DB」 장, `record_cards` FK 대상(사본 모양을 정할 때) |
| M2 | [../kafka.md](../kafka.md) 「이름」 「토픽」, [../cdc.md](../cdc.md), [../outbox-inbox.md](../outbox-inbox.md) 「지금 있는 이벤트」 「받는 쪽 — Inbox」, [../README.md](../README.md) 「이벤트 색인」 「구성 — 지금」 | 만들고 검증한 PR에서 — 이름은 platform 「새 이름」에서 옮긴다 |
| ~~날씨 자리를 정할 때~~ | — | **정했다 → D-63** — 세션이 끝낼 때 지금과 같은 기준으로 조회해 값 · 시점이 같다. 고칠 문서 없음 |
| M4 | api-spec 7-1 · 2-2 | 7-1은 반영된 뒤 보인다(D-58). `/users/me/status`는 반영 전 `RUNNING`일 수 있다(D-31) |
| M5 | verification 「판정과 결과」, load_test/msa RESULTS, rationale 「근거 순위」 | 판정 |
| M6 | [../README.md](../README.md) 「서비스 이름」, [README.md](README.md) 「저장소를 나눌 때」, CLAUDE.md · 루트 README | 실제 이름, runtime.md · erd 러닝 DB 장 옮기기, 문서 색인 |
| M7 | api-spec 6-1 · 6-2, architecture 「공개 경로」, msa README 「구성 — 지금」, platform 「지금 (11-24 기준)」 | 응답 주인 표시(세션), 세션 규칙을 운영 중으로, 새 날짜의 지금 구성 |
| M8 | 레이어 규칙 ([docs/architecture.md](../../architecture.md)), erd 2장 | 옛 엔진 목록 규칙 · WS 직접 import 예외를 지움, 메인에 남는 러닝 테이블만 |
| M9 | 이 문서 | 상태 줄을 기록으로 |

## 정하지 않은 것

1. **모놀리스 안 영속성** — (가) 메인 영속성 설정을 `@Primary`로 명시한다(메인을 건드린다) (나) 모듈이 `HikariDataSource`를 빈으로 내지 않고 이름 붙인 `TransactionTemplate` · `JdbcClient`만 낸다. 쓰기가 적고 구간 묶음 삽입에 맞아 (나)를 권한다. M1 전에 정한다.
2. **호스팅 동안 러닝 DB vs 메인 DB 안 스키마** — 러닝 DB RDS가 생기기 전 모듈이 어디를 가리키는가. 스키마면 슬롯 하나로 싣지만 러닝 DB로 옮길 때 데이터 이동이 생긴다. 러닝 DB면 두 번째 커넥터를 M2에 만든다.
3. **Flyway를 들이는 때** — M1 vs 저장소를 나눌 때(M6) (D-56).
4. **힙 `MaxRAMPercentage` 값** — 75 / 70 / 65 (F8). SC11(J1–J4) 결과로 정한다.
5. **옛 기록** — 「옛 기록」.
6. **엔진을 적는 자리** — `running_rooms`의 열(손 DDL) vs Redis 키(TTL 7시간 이상).
7. **운영에서 F9를 거는 방법** — ASG 인스턴스 새로 고침이 컨테이너에 정상 종료 신호를 주고 기다리게 하는 연결.
8. **M6 · M7 순서** — `제안 (D-62)`. 운영 추출 뒤로 정하면 번호는 그대로 두고 단계 순서만 바꾼다.
