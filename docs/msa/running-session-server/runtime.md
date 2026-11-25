# 러닝 세션 서버 — 런타임

러닝 세션 서버 안에서 러닝 하나가 `RUNNING_START`부터 `RUNNING_FINISHED`까지 인스턴스 교체 · 재시작 · 배포를 지나 안전하게 가는 법을 적는다. 시작과 다시 붙기, 러닝 중 경로, 끝내기, 강제 종료와 청소기, 다중 인스턴스, 연결 시간 제한, 계획 종료, 앱이 따르는 재접속 정책이 여기 있다. 세션 안의 동작을 바꿀 때 여기서 시작한다.

> 상태: 계획 — 새 엔진을 만드는 M3까지 목표 동작이다. 지금 모놀리스에서 같은 일을 하는 코드는 클래스 이름으로 적는다. 절과 행마다 `운영 중`(지금 동작을 그대로 가져간다) · `정함 (D-xx)` · `제안` · `정하지 않음`을 단다.

| 무엇 | 어디 |
|---|---|
| 메인과의 경계 · 넘기기 · 이벤트 · 동기 호출 | [architecture.md](architecture.md) |
| 불변식 · 탈퇴 saga · 대조 | [consistency.md](consistency.md) |
| 고칠 차례 (F1–F9) · 새 엔진 단계 (M3) | [migration.md](migration.md) 「선행 수정 F1–F9」 「단계 M0–M9」 |
| 파드 값 (preStop · 유예 · 등록 해제) | [eks.md](../eks.md) 「계획 종료」 |
| Redis 키 · 버킷 · 확인할 운영 값 | [platform.md](../platform.md) 「Redis」 「S3」 「확인할 운영 값」 |
| 단계 진행 | [README.md](README.md) 「진행 단계」 |

> 저장소를 나누면(M6) 이 파일과 erd의 러닝 DB 장이 새 저장소로 간다 — [README.md](README.md) 「저장소를 나눌 때」. 다른 문서는 이 파일을 절이 아니라 파일로 링크한다.

## 따르는 규칙과 계약

링크만 둔다. 이 서버가 지키고, 혼자 바꾸지 않는 것이다.

- **레이어 · 다중 인스턴스** — 레이어 규칙 ([docs/architecture.md](../../architecture.md)), 특히 「다중 인스턴스」. 저장소를 나눈 뒤의 규칙은 [migration.md](migration.md) 「새 저장소가 따르는 규칙」.
- **로그 · 메트릭** — [logging-convention.md](../../logging-convention.md), [metrics-convention.md](../../metrics-convention.md) (세션 지표 이름은 예정 행).
- **공개 계약** — [api-spec.md](../../api-spec.md) 5(5-C · 5-D 러닝 WebSocket) · 6-1 · 6-2는 그대로다 (D-26 · D-58). 바꾸려면 api-spec과 [decisions.md](../decisions.md)에 먼저 쓰고 iOS · 안드로이드 최소 버전 상향과 묶는다.
- **기록 규칙** — [feature-spec.md](../../feature-spec.md) 도메인 제약의 GPS 트랙 · 트랙 필터 · 러닝 콤보.

## 시작 · 재연결

`RUNNING_START` 하나가 첫 진입과 다시 붙기를 함께 맡는다(api-spec 5-D) — 운영 중, 그대로 가져간다.

**지금** (모놀리스)

1. 핸드셰이크 — `AppVersionFilter`(요청마다 `app_versions` 조회), `JwtHandshakeInterceptor`(HS256 검증 + `BlockedTokenValidator`의 Redis 조회).
2. `StartRunningHandler` — 서비스 DB 트랜잭션에서 참가자 → 방을 `FOR UPDATE`로 잠그고 다시 잇기 · 방 시작 · 참가자 시작.
3. `RegisterRunningSessionHandler` — 이 인스턴스 연결표에 등록(옛 연결은 `4001`로 닫는다) → 방 채널 구독 → `SUPERSEDE` 발행.
4. 화면 상태 `RUNNING` → `StartRunningComboHandler`(다시 붙기면 건드리지 않는다) → `GetRunningSnapshotHandler`(`LoadPlayerProfilesPort` + `GenerateViewUrlPort`로 프로필 URL) → `RUNNING_STARTED`.
5. 다시 붙으면 1–4를 모두 다시 하고, 앱은 로컬 트랙을 순번 0부터 다시 보낸다. `RunningTrackRedisAdapter`의 순번 비트(`SETBIT`)가 중복을 거른다.

**목표** — 제안

- **확정 직후 붙는다** — 앱은 `MATCH_CONFIRMED`를 받으면 러닝 WS에 붙어 대기 화면(멤버 변화 · 시작 카운트다운)을 받고, 확정 뒤 이탈도 세션에 보낸다(D-73). 시작 전 대기 연결은 `HEALTH_CHECK`로 유지한다 — 동시 연결이 시작 10분 전부터 늘고, 세션 확장 기준(WS 연결 게이지)이 이것을 센다.
- **메인을 부르지 않는다** — 참가자 · 몸무게 · 프로필은 넘기기로 받은 세션 자기 행에서 읽는다. 예외는 세션이 모르는 방 하나다 — 당겨 오기([architecture.md](architecture.md) 「동기 호출 허용 목록」 1, 방 하나에 한 번으로 묶는다, D-71). 당겨 오지 못하면 `RUNNING_SESSION_UNAVAILABLE`이고 앱은 지금처럼 다시 보낸다.
- **토큰은 로컬에서 검증한다** — ES256 공개 키 (D-30 제안). 폐기된 토큰과 최소 버전 관문은 [architecture.md](architecture.md) 「인증」 「정하지 않은 것」.
- **순서는 그대로다** — 등록 → 화면 상태 → 콤보 → 스냅샷 → ack. 바꾸면 자기 상태 통지나 방금 붙은 콤보를 놓친다(`RunningWebSocketHandler`의 순서 주석).
- **잠금은 참가자 → 러닝 세션 순이다** — 끝내기 · 강제 종료 · 이탈 · 정산과 같은 순서다.
- **오류 코드는 그대로다** — `NOT_ROOM_PLAYER` · `INVALID_ROOM_STATE` · `RUNNING_SESSION_UNAVAILABLE`.
- **다시 붙기의 빠른 길** — 세션 Redis에 이 방의 화면 상태가 `RUNNING` · `PAUSED` · `DISCONNECTED`로 있으면(커밋된 시작 뒤에만 쓰인다) DB 트랜잭션을 건너뛴다. 끊긴 자리를 다시 잇는 경우만 DB를 탄다. 배포 · 장애 뒤 한꺼번에 다시 붙는 폭주(SC5)의 잠금을 줄인다.

## 위치 · 일시정지 · 전파

`운영 중` — 지금 경로를 그대로 가져간다. 달라지는 것은 Redis와 리스너 컨테이너가 세션 것이 되는 것뿐이다.

- **SQL이 없다** — `UpdateRunningLocationHandler`에 트랜잭션이 없고 Redis만 쓴다(트랙 저장 · 누적 거리 · 화면 상태 · 진행 발행 · 콤보 판정). 목표 도달 자동 종료만 「끝내기」로 간다.
- **끝난 참가자의 늦은 배치는 받지 않는다** — 화면 상태가 `FINISHED`면 저장하지 않고 `RUNNING_FINISHED`만 다시 보내 앱이 로컬 트랙을 지우게 한다 (I3).
- **재전송분은 판정하지 않는다** — 다시 붙은 뒤 오는 이미 받은 순번은 저장 · 화면 상태 · 콤보를 바꾸지 않는다.
- **끊김만으로 콤보가 끊기지 않는다** — 좌표가 `running-combo.freshness`(19초)보다 오래 안 오면 관측 없음으로 보고 쌓지도 깎지도 않는다. 다시 붙어 재전송이 따라잡은 뒤의 거리 차로 판정한다. 끊는 것은 일시정지 · 끝남, 그리고 창(`running-combo.exit-meters` 30m) 밖이거나 한쪽이 서 있는 판정이 `running-combo.miss-allowance`(1)번을 넘게 이어질 때다 — feature-spec 러닝 콤보.
- **실패 처리가 다르다** — `SUPERSEDE` 발행이 실패하면 `RUNNING_SESSION_UNAVAILABLE`로 시작을 막는다(좌표도 담지 못할 상태다). 진행 · 콤보 발행 실패는 로그만 남긴다.
- **pub/sub은 세션 인스턴스 사이에만 쓴다** — 방 채널 `running:room:{id}`가 진행 · 콤보 · `SUPERSEDE`를 나른다. 서비스 사이 pub/sub은 없다 (D-49, [README.md](../README.md) 「통신 — 어디로 무엇이 지나가는가」).
- **세션 자기 리스너 컨테이너에 상한 있는 실행기를 둔다** — 제안. 모놀리스에서는 F7로 먼저 한다.
- **지금 리스너 컨테이너** — `runningChannelContainer` 빈 하나를 러닝 · 매칭 · 알림 · 예약 채널이 같이 쓰고, 실행기를 정하지 않았다(`RunningChannelConfig`).

## 끝내기

`정함 (D-24)` — 기록 + 상태 + 아웃박스를 러닝 DB 한 트랜잭션으로 커밋한 뒤 ack한다 (I1). 판정 → 봉인 → 확정으로 나누는 모양은 `정함 (D-63, D-79, D-82)` — 옛 엔진에도 M0에서 먼저 넣고, 새 엔진도 같은 구조로 짠다.

```text
RUNNING_FINISH ──┐
목표 도달 배치 ──┴─▶ 임대 잡기 (아무것도 쥐지 않은 채 SET NX, 토큰, TTL 30초)
                      못 잡으면: RUNNING_FINISH는 ERROR RUNNING_TRACK_UNAVAILABLE, 자동 종료는 무응답
                  ─▶ 판정 (트랜잭션 하나, 참가자 → 러닝 세션 잠금)
                      이미 끝났으면 ack만 다시
                      Redis 원본 트랙 읽기(스냅숏) → TrackFilter → 목표 판정 — 미달이면 미룬다
                  ─▶ 봉인 (잠금 · 트랜잭션 없음, 임대만) — 계산과 느린 외부 호출
                      스냅숏으로 TrackAnalyzer → (기록이 남을 때만) S3 원본 트랙 올리기 → 날씨 조회
                  ─▶ 확정 (러닝 DB 트랜잭션 하나, 참가자 → 러닝 세션 잠금)
                      이미 끝났으면 저장 없이 ack만
                      기록 · 세그먼트 · 구간 → 참가자 상태 · 페널티 → 마지막이면 방 닫기
                      아웃박스 행 (참가자 결과 — 마지막이면 방 상태 FINISHED/CANCELLED를 싣는다, D-72) → 커밋
                  ─▶ 커밋 뒤: 화면 상태 FINISHED + 진행 발행
                  ─▶ 임대 지우기 (자기 토큰만 — 모든 경로 · 실패도 finally 하나)
                  ─▶ RUNNING_FINISHED (ack)
                  ─▶ Redis 정리: 트랙 삭제 · 콤보 끊기 · 방이 닫혔으면 콤보 보관
```

| 지킬 순서 | 깨지면 |
|---|---|
| ack는 커밋 뒤다 | 앱이 로컬 트랙을 지운 뒤 기록이 롤백되면 그 러닝은 되살릴 수 없다 (I1) |
| `FINISHED` 표시는 ack 앞이다 | ack를 받은 앱이 닫는 연결의 `DISCONNECTED`나 늦은 배치가 끝난 상태를 덮는다 (I3) |
| Redis 트랙은 S3 업로드와 커밋 뒤에만 지운다 | 다시 시도할 원본이 없다 |
| 확정 트랜잭션 안에서 바깥을 부르지 않는다 — S3 · 날씨 · 메인 | 잠금과 커넥션을 쥔 채 기다려 끝내기 몰림에서 무너진다 — run 14, [rationale.md](rationale.md) 「G4 끝내기 폭주는 고칠 결함」 |
| 평균 페이스 · 쿨다운은 메인이 반영 때 한다 — 세션은 절대 시각 `cooldownUntil`만 싣는다 | 끝내기가 메인에 동기로 기댄다 — [rationale.md](rationale.md) 「G3 장애 격리」 |
| 목표 판정은 잠금 안이다 (D-80). 분석은 판정의 스냅숏으로 봉인에서 한다 (D-82) | 판정이 밖이면 미달 판정이 겹친 끝내기와 어긋난다. 분석이 다른 트랙을 읽으면 올린 원본 · 저장한 기록이 어긋난다 |
| 임대는 판정 앞에서, 쥔 것 없이 한 번만 시도한다 (D-82) | 잠금을 쥔 채 임대를 기다리면 확정(임대를 쥔 채 잠금을 기다림)과 교착이다. 판정 트랜잭션 안에서 잡으면 커밋 실패 때 토큰을 잃는다 |
| 임대는 자기 토큰일 때만, 실패해도 지운다 (D-82) | 남의 임대를 지워 봉인이 겹치거나, 실패한 임대가 TTL까지 끝내기를 막는다 |

- **멱등이다** — 이미 확정된 참가자에게는 기록을 덮어쓰지 않고 ack만 다시 보낸다(지금 `RunningFinisher.confirm`과 같다). 앱의 `RUNNING_FINISH` 재전송과 다음 위치 배치가 다시 시도다.
- **목표 도달 자동 종료도 같은 길이다** — 위치 배치가 목표를 채우면 `UpdateRunningFinishJudge` 자리에서 같은 봉인 → 확정을 탄다. 몸무게는 넘기기 스냅샷에서 읽는다.
- **날씨는 봉인에서 조회한다** — `정함 (D-63)`. 지금처럼 출발 좌표 · 출발 시각 기준이고, 실패하면 기본값이라 끝내기를 막지 않는다. 확정 트랜잭션 안에서는 부르지 않는다.
- **커밋 뒤 ack 전에 프로세스가 죽으면** — 기록은 남고 앱은 ack를 받지 못한다 → [consistency.md](consistency.md) 「정하지 않은 것」.

## 강제 종료 · 청소기

`제안 (D-29)` — 예약 작업은 세션 자기 `scheduled_jobs`와 채널로 돈다. `RUNNING_START` · `RUNNING_FORCE_FINISH`는 넘기기 때 건다.

| 작업 | 언제 | 하는 일 | 상태 |
|---|---|---|---|
| 강제 종료 | 시작 + 6시간 (`match.force-finish-offset`)을 알람으로 | 알람이 잠금 없이 확인하고, 할 일이 있을 때만 참가자 → 방을 잠가 노쇼(`JOINED`) 이탈 · 방 닫기(페널티면 `cooldownUntil`을 싣는다). 뛰던 사람이 남으면 예약을 1분 뒤로 다시 걸고 러너마다 워커가 「끝내기」 그대로 확정한다. 마지막 러너가 방을 닫고, 모두 끝난 것을 본 알람이 완료로 적는다. 상한은 트랙 TTL 7시간(`running-track.ttl`). 마지막 참가자 결과 이벤트에 방 상태를 싣는다(D-72) | 정함 (D-83) — 옛 엔진에는 F3로 먼저 |
| 혼자 달리기 청소기 | 주기 — 기준 시간을 넘긴 `RUNNING` 혼자 달리기 | 강제 종료와 같은 확정. 기준 시간은 「정하지 않은 것」 | 제안 |
| 밀린 작업 훑기 | 30초마다 | 시각이 지났는데 끝나지 않은 예약을 다시 실행한다 | 제안 |
| 콤보 보관 | 방 닫힘 커밋 뒤 | 콤보 구간을 러닝 DB에 남긴다. 실패해도 종료는 그대로다(지금 `RunningComboArchiver`) | 운영 중 — 세션 안의 로컬 단계 |

- **강제 종료의 지킬 순서 (D-83)** — 잠금 순서는 예약 행 → 참가자 → 방. 잠금 없이 읽은 값으로 고치지 않는다(잠그고 다시 읽는다). 워커는 알람 커밋 뒤에 보낸다. 예약은 실행 전에 다시 걸고, 다 끝난 것을 본 알람만 완료로 적는다.
- **지금 코드의 문제와 고칠 차례** — 방 하나를 트랜잭션 하나로 묶은 강제 종료(`ForceFinishRunningRoomHandler`)는 F3([migration.md](migration.md) 「선행 수정 F1–F9」, D-83). 실패한 타이머가 기동 때만 회복되는 것(`JobTimerAdapter` · `ScheduledJobRecoveryRunner`)은 F1–F9에 없다 — 세션은 위 표의 밀린 작업 훑기로 막는다.
- **콤보 보관은 saga도 넘기기도 아니다** — 세션 Redis와 러닝 DB가 모두 세션 것이라 커밋 뒤 로컬 단계로 남는다.
- **마지막 그물은 메인의 대조다** — [consistency.md](consistency.md) 「대조」.

## 다중 인스턴스

`운영 중` — 지금 러닝 모델을 그대로 가져간다. 규칙은 레이어 규칙 ([docs/architecture.md](../../architecture.md)) 「다중 인스턴스」.

| 경우 | 동작 |
|---|---|
| 같은 인스턴스에서 갈아타기 | `RegisterRunningSessionHandler`가 연결표에서 옛 연결을 꺼내 바로 `4001`로 닫는다. 옛 연결의 종료 처리는 '내 연결일 때만 정리' 가드에 걸려 건너뛴다 |
| 다른 인스턴스로 다시 붙기 | 새 연결이 먼저 붙고 방 채널에 `SUPERSEDE`(이긴 연결 ID)를 발행한다. 인스턴스마다 자기 표만 보고 진 연결을 닫는다(`CloseSupersededSessionHandler`). A(1) → B(2) → C(1)로 이어 붙어도 C만 남는다 — 앱 두 대 E2E(11-11) |
| 늦게 도는 종료 처리 | `RemoveRunningSessionHandler`는 자기 연결이 빠졌을 때만 명부에서 빼고 `DISCONNECTED`를 알린다. `FINISHED` 뒤에는 바뀌지 않는다 (I3) |

- **알고 두는 것 — 늦은 `DISCONNECTED`** — 다른 인스턴스로 다시 붙으면 옛 인스턴스의 종료 콜백이 가드를 통과해(그 인스턴스 표에는 아직 옛 연결이 있다) `DISCONNECTED`를 알린다. 상대 화면은 다음 좌표(약 10초)에 `RUNNING`으로 돌아온다 — api-spec 5-D가 허용한다. 고칠 안은 `CloseSupersededSessionHandler`가 닫기 전에 표에서 빼고 명부에서 나가는 것이다(매칭 `CloseSupersededMatchStreamHandler`와 같은 모양). k6에서는 `false_disconnected_broadcast`로 센다.
- **탈퇴 소켓 닫기는 모든 인스턴스에 간다** — 정함 (D-44, D-70, D-72). 사용자 변경 이벤트(삭제)를 받은 인스턴스가 세션 Redis로 알린다 ([consistency.md](consistency.md) 「계정 탈퇴 saga」 5).
- **테스트** — 기본 E2E는 앱 한 대다. 다른 인스턴스 경우는 두 번째 인스턴스를 `JPA_DDL=none`으로 띄우고 `E2E_SECOND_BASE_URL`을 줘야 `MultiInstanceE2eTest`가 돈다.

## 연결 시간 제한 사슬

앞 고리가 뒤 고리보다 짧아야 한다. 이 사슬의 값은 여기에만 둔다.

| 고리 | 값 | 어디서 정하나 | 상태 |
|---|---|---|---|
| 앱 `HEALTH_CHECK` 주기 | 30초 이하 — 시작 전 대기 · 일시정지 중에만 뜻이 있다(러닝 중에는 좌표가 대신한다) | 앱 — 「앱 재접속 정책」 | 정하지 않음 |
| 서버 WS 유휴 | 120초 | `websocket.idle-timeout=2m` | 운영 중 |
| ALB 유휴 | 150초 | ALB 속성 — EKS면 Ingress 주석([eks.md](../eks.md) 「엣지 · 자동 확장 · 배포」) | 제안 — 운영 값은 확인 전 |
| Tomcat keep-alive | 180초 | `server.tomcat.keep-alive-timeout` — 지금 설정하지 않았다 | 제안 |

- **ALB가 서버보다 먼저 끊지 않는다** — 조용한 소켓을 닫는 시각을 서버 설정(120초) 하나가 정한다.
- **ALB 유휴는 keep-alive보다 짧다** — ALB가 서버가 막 닫은 연결을 다시 쓰지 않는다(502).
- **위험** — 운영 ALB 유휴가 기본 60초라면(SSE 설정 주석이 60초를 가정한다) WS 유휴 120초보다 짧아, `HEALTH_CHECK` 주기가 60초를 넘으면 시작 전 조용한 소켓을 ALB가 먼저 끊는다. 운영 값은 [platform.md](../platform.md) 「확인할 운영 값」.
- **메인 SSE도 같은 ALB에 걸린다** — keep-alive 15초(`match-stream.keep-alive-interval` · `app-stream.keep-alive-interval`)라 ALB 유휴가 60초든 150초든 맞다.

## 계획 종료

`정함 (D-65)` — 인스턴스 · 파드를 내릴 때 소켓을 `4500`으로 닫는다(닫기 코드 재사용은 `정함 (D-42)`). 앱은 이미 `4500`을 다시 붙기로 처리한다(api-spec 5). 지금은 드레인이 없다 — 배포가 모든 소켓을 끊는 문제는 [rationale.md](rationale.md) 「G1 배포 결합」.

드레이너(이름 후보 `WebSocketDrainer`)가 `SmartLifecycle`로 웹 서버 graceful보다 먼저 돈다. 종료 신호(SIGTERM)를 받으면:

1. **준비 상태(readiness)를 내린다** — 대상 그룹 검사가 readiness를 보게 하는 것은 F6.
2. **새 `RUNNING_START`는 `RUNNING_SESSION_UNAVAILABLE`로 거절한다** — 앱은 다시 보내고, 곧 닫힌 뒤 다른 인스턴스로 붙는다.
3. **열린 소켓을 `4500`으로 30초에 걸쳐 나눠 닫는다(무작위 간격)** — 한꺼번에 닫으면 남은 인스턴스로 `RUNNING_START`가 몰린다(SC5).
4. **끝내기 중인 소켓은 ack를 보낸 뒤 닫는다** — 커밋 뒤 ack 전 끊김을 계획 종료가 늘리지 않는다.
5. **Spring graceful 20초** (`server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase`) — graceful은 업그레이드된 WS를 닫지 않으므로 3이 먼저다.
6. **남은 소켓은 프로세스 종료로 `1006`이 된다.**

| 앱이 쓰는 시간 | 값 |
|---|---|
| 나눠 닫기 | 30초 |
| graceful | 20초 |
| 합 | 50초 — 플랫폼의 종료 유예와 ALB 등록 해제 지연은 preStop + 이 합보다 길어야 한다 |

- **플랫폼 값은 따로 둔다** — 지금 ASG에 쓸 값(M0의 F9)은 [migration.md](migration.md) 「선행 수정 F1–F9」, EKS 파드 값은 [eks.md](../eks.md) 「계획 종료」.
- **메인 SSE 드레인은 세션 일이 아니다** — [eks.md](../eks.md) 「계획 종료」의 main-api 행, [migration.md](migration.md) 「선행 조건」.
- **계획 밖 끊김(`1006`)** — OOM · 노드 · AZ 장애는 드레인 없이 끊긴다. 앱은 ack 전까지 로컬 트랙을 지우지 않아 잃는 것은 없고, 다시 붙어 `RUNNING_START` → 재전송으로 이어 간다. 앱이 무작위 지연 없이 바로 다시 붙으면 동시에 몰린 `RUNNING_START`가 된다 — 「앱 재접속 정책」.

## 앱 재접속 정책

`정하지 않음` — 값은 노션 프론트 가이드에 있다 — 옮겨 적으면 '앱 값' 칸을 채운다. 하네스는 그때까지 임시값을 쓰고, 앱 값이 오면 바꾼다.

| 값 | 쓰는 곳 | 저장소에 있는 것 | 앱 값 | 하네스 임시값 |
|---|---|---|---|---|
| 위치 배치 주기 | 진행 · 콤보 해상도 | 약 10초 (api-spec 5-D) | — | 10초 |
| 다시 붙기 간격 (backoff · 무작위 지연) | 계획 종료 · 장애 뒤 몰림 — SC1 · SC2 · SC5 | 없음 | 비어 있음 | 1초 + 0–2초 무작위, 두 배씩 30초까지 |
| 끝내기 재시도 · ack 기다림 | I1 · SC9 | `RUNNING_FINISH`는 멱등 (api-spec 5-D) | 비어 있음 | 15초 기다린 뒤 2 · 4 · 8 · 16초 |
| 재전송 묶음 크기 · 간격 | 다시 붙은 뒤의 부하 | 트랙 전체를 짧은 간격으로 다시 보낸다(`UpdateRunningLocationHandler` 주석) | 비어 있음 | 비어 있음 |
| `HEALTH_CHECK` 주기 | 「연결 시간 제한 사슬」 | '주기적으로' (api-spec 5) | 비어 있음 | 30초 |
| `1006`도 `4500`처럼 다시 붙는가 | 계획 밖 끊김 | `4500`은 다시 붙고 `4001`은 붙지 않는다 (api-spec 5) | 비어 있음 | 다시 붙는다 |
| `RUNNING_START`가 `NOT_ROOM_PLAYER`일 때 | ack를 잃은 끝내기 ([consistency.md](consistency.md) 「정하지 않은 것」) | 없음 | 비어 있음 | — |

## 러닝 DB · Redis 키

링크만 둔다.

- **테이블과 주인** — [architecture.md](architecture.md) 「데이터 주인」.
- **러닝 DB 스키마** — erd(정하면 「러닝 DB」 장). 저장소를 나눈 뒤에는 새 저장소의 Flyway가 정본이다 (D-56).
- **러닝 DB 커넥터 · 슬롯** — [platform.md](../platform.md) 「러닝 DB CDC」.
- **Redis 키 · 채널 · 인스턴스 설정** — [platform.md](../platform.md) 「Redis」.
- **원본 트랙 버킷** — [platform.md](../platform.md) 「S3」.

## 정하지 않은 것

1. ~~`TrackAnalyzer`를 봉인에 둘지 확정에 둘지~~ — **정했다 → D-81 → D-82**: 목표 판정은 잠금 안, 분석은 임대를 쥔 봉인에서.
2. ~~원본 트랙 S3 key~~ — **정했다 → D-82**: 고정 key 그대로. 같은 러너의 봉인은 임대가 한 줄로 세우고, 겹쳐도 나중 원본이 덮을 뿐이다.
3. ~~계획 종료 닫기 코드~~ — **정했다 → D-42**: `4500`을 다시 쓴다.
4. **프로필 URL** — (가) 세션이 사용자 자산 버킷 읽기 권한으로 직접 만든다([architecture.md](architecture.md) 「데이터 주인」의 후보). (나) 넘기기 때 메인이 URL을 실어 보낸다 — 만료가 러닝보다 짧으면 다시 붙을 때 깨진다.
5. **앱 재접속 값** — 「앱 재접속 정책」의 빈칸 — 노션 프론트 가이드에서 옮겨 적는다.
6. **늦은 `DISCONNECTED`를 고치는 범위** — `CloseSupersededSessionHandler`가 닫기 전에 표에서 빼는 안을 모놀리스에서 먼저 고칠지(M0), 새 엔진에서만 할지.
7. **혼자 달리기 청소기 기준 시간** — 매칭 방과 같은 시작 + 6시간인지, 트랙 TTL 7시간보다 짧은 다른 값인지.
