# 러닝 세션 서버 — 메인과의 구조

메인 서버(running-service)와 러닝 세션 서버 사이의 목표 구조다. 선을 어디에 긋는지, 데이터마다 주인이 누구인지, 선을 넘는 것(넘기기·이벤트·허용된 동기 호출·인증)이 무엇인지, 메인이 사본으로 무엇을 갖는지, 공개 계약이 어떻게 그대로 남는지를 적는다. 세션과 메인 사이에 무언가를 더하거나 바꿀 때 여기서 시작한다.

> 상태: 목표 설계 — 아직 코드에 없다. 절과 행마다 `정함 (D-xx)` · `제안` · `정하지 않음`을 단다. 이 문서는 저장소를 나눈 뒤에도 메인 저장소에 남는다([ai-server/architecture.md](../ai-server/architecture.md)와 짝).

| 무엇 | 어디 |
|---|---|
| 목표 구성도 (서비스 셋 · 넘기기 · 이벤트 · Redis 둘 · 러닝 DB) | [README.md](../README.md) 「구성 — 목표」 |
| 왜 나누는가 | [rationale.md](rationale.md) |
| 지금 코드가 얽힌 곳 (숫자) | [migration.md](migration.md) 「지금 얽힌 곳 (11-24 기준)」 |
| 불변식 · 탈퇴 saga · 대조 | [consistency.md](consistency.md) |
| 세션 안 (시작 · 끝내기 · 계획 종료) | [runtime.md](runtime.md) |
| 결정과 바뀐 이력 | [decisions.md](../decisions.md) |
| Redis 키 · 버킷 · 새 이름 | [platform.md](../platform.md) 「Redis」 「S3」 「새 이름」 |
| 지금 계약 | [api-spec.md](../../api-spec.md) 5 · 6-1 · 6-2 · 7-1 · 9-5 · 12-4 — 서비스 사이 계약은 정하면 14장 |
| 단계 진행 | [README.md](README.md) 「진행 단계」 |

> 주소·키·비밀값은 적지 않는다 — Parameter Store 이름까지만.

- **뒤집기 전에 결정부터** — 여기 적힌 것을 바꾸려면 [decisions.md](../decisions.md)에 항목을 먼저 쓰고 같은 PR에서 이 문서를 고친다.
- **본문이 정해지면 계약으로 옮긴다** — 이벤트·내부 API의 본문은 코드보다 먼저 api-spec 14장에 쓰고, 여기에는 행과 링크만 남긴다.
- 서비스 이름은 [README.md](../README.md) 「서비스 이름」, 레이어는 레이어 규칙([docs/architecture.md](../../architecture.md))을 따른다.

## 이 구조가 지키는 것

`정함 (D-24 · D-26)`에서 나오는 성질이다. 하나라도 깨지면 구조를 다시 본다.

| 성질 | 근거 |
|---|---|
| **러닝 중 경로가 메인 없이 돈다** — 위치 배치는 세션 자기 Redis만 쓴다 | [rationale.md](rationale.md) 「G3 장애 격리」 · 지금도 위치 배치에 SQL이 없다(`UpdateRunningLocationHandler`에 트랜잭션 없음) |
| **기록은 쓰는 쪽에 산다** — 기록 + 참가자·방 상태 + 아웃박스 행을 러닝 DB 한 트랜잭션으로 커밋한 뒤 `RUNNING_FINISHED`를 보낸다. ack는 '메인이 안다'가 아니라 '기록이 남았다'다 | [consistency.md](consistency.md) 「불변식 I1–I7」 (I1) |
| **메인의 반응은 파생 상태다** — 자리 풀기·쿨다운·평균 페이스·기록 목록은 세션 결정을 따라 할 뿐이라 되돌릴 일이 없다. 이미 쓰는 아웃박스 → Debezium → inbox를 그대로 쓴다 | [outbox-inbox.md](../outbox-inbox.md) |
| **시작·끝내기 폭주는 세션 자기 풀과 DB에 걸린다** — 메인 커넥션 풀과 서비스 DB를 나눠 쓰지 않는다 | [rationale.md](rationale.md) 「G4 끝내기 폭주는 고칠 결함」 |
| **원본 트랙은 세션만 쓴다** — S3 `gps-tracks`(`GpsTrackS3Adapter`), 메인은 읽지 않는다 | 「데이터 주인」 |
| **AI 서버는 자기 DB를 갖고 Kafka로만 말한다** | [ai-server/architecture.md](../ai-server/architecture.md) 「경계」 |
| **워치는 폰을 거친다** — 인증하고 라우팅할 세 번째 클라이언트 경로가 없다 | api-spec 2-3 |

깨지는 경우 — ack를 커밋 전에 보낸다 / S3에 트랙이 올라가기 전에 Redis 트랙을 지운다 / 평균 페이스·쿨다운을 끝내기 안에서 메인에 동기로 묻는다. 끝내기 순서는 [runtime.md](runtime.md).

## 경계 — 생애 단계

`정함 (D-26)` — 명단이 정해질 때까지는 메인이, 그 뒤 러닝은 세션이 맡는다. 단계마다 쓰는 쪽은 하나다. 넘기는 방식은 `정하지 않음`(「넘기기」).

| 단계 | 주인 | 방 상태 | 참가자 상태 | 하는 일 |
|---|---|---|---|---|
| 모집 | 메인 | `MATCHING` → `MATCHED` (시작 전 취소 `CANCELLED`) | 신청 자리(`running_players`, `deleted_at IS NULL`) | 신청·합류·초대·수락·거절·나가기·방 평균 페이스 다시 계산·모집 마감 |
| 러닝 | 세션 | `MATCHED` → `STARTED` → `FINISHED` / `CANCELLED` | `JOINED` → `RUNNING` → `COMPLETED` / `RUNNING_LEFT_*` / 노쇼 `MATCHED_LEFT_*` | 시작·다시 붙기·끝내기·강제 종료·확정 뒤 이탈·기록·구간·콤보 |

- **넘기는 순간은 `RunningRoom.closeMatching()`이다** — 매칭 방은 시작 10분 전(`match.close-offset`), 초대 방은 1분 전(`invite.close-offset`)에 `CloseMatchingHandler`가 부른다. 혼자 달리기는 `MATCHED`로 태어나므로 만들 때 넘긴다.
- **확정 뒤 명단은 메인 쪽 전이로는 줄기만 한다** — `join()`(`canJoin()`)과 `invite()`가 `MATCHING`을 요구한다. 다시 붙기(`rejoin()`)는 러닝 단계의 일이라 세션 몫이다. 그래서 확정 순간의 스냅샷이 넘기기 지점으로 충분하다.
- **잠금 순서는 서비스 안에만 있다** — 메인은 신청 → 방, 세션은 참가자 → 러닝 세션. DB를 넘는 잠금은 없다. 단 하나 남는 경합(확정 뒤 나가기 vs `RUNNING_START`)은 [consistency.md](consistency.md) 「확정 뒤 이탈」.
- **'러닝은 수집, 메인은 표시'로 긋지 않는다** — 매칭과 러닝이 한 애그리거트(같은 행·같은 잠금 순서·같은 어댑터)라 그 선은 saga로도 잇지 못한다. 숫자는 [migration.md](migration.md) 「지금 얽힌 곳 (11-24 기준)」. 수집·표시 구분은 데이터 주인으로만 남는다([rationale.md](rationale.md) 「받쳐 주는 근거 G5–G8」 G8).

## 메인 쪽 불변식

`제안 (D-27)` — [consistency.md](consistency.md) 「불변식 I1–I7」의 I4로 확인한다.

- **`runningRoomId`는 메인 혼자 매긴다** — 혼자 달리기 방도 같다. 공개 API의 방 번호는 정수 하나의 공간이라, 세션이 자기 번호로 방을 만들면 겹친다.
- **사용자마다 활성 신청은 하나다** — 지금처럼 `running_players`가 그 자리이고 `ExistsActiveApplicationPort`(신청·초대 열기·초대 수락)와 `ExistsActiveRunningPlayerPort`(혼자 달리기)가 지킨다.
- **넘긴 자리는 메인이 혼자 풀지 않는다** — 세션 이벤트(참가자 완주·참가자 이탈)나 이탈 응답으로만 푼다. 이중 예약은 생기지 않고, 대가는 반영 전까지 사용자가 활성으로 보이는 것이다([consistency.md](consistency.md) 「종료 뒤 최종 일관 창」, D-31 `제안`).
- **혼자 달리기 생성은 메인에 남는다** — 세션이 만들면 메인 자리에 대한 예약 saga와 청소기가 하나 더 생긴다.

## 데이터 주인

목표 주인의 단일 집이다. Redis 키·채널은 [platform.md](../platform.md) 「Redis」, 러닝 DB 스키마는 erd(정하면 「러닝 DB」 장), 버킷은 [platform.md](../platform.md) 「S3」.

| 데이터 | 목표 주인 | 다른 쪽이 얻는 법 | 상태 |
|---|---|---|---|
| `running_rooms` 모집 단계 · 방 번호 | 메인 | 세션은 넘기기로 받는다 | 정함 (D-26) · 번호는 제안 (D-27) |
| `running_rooms` 러닝 단계 (`STARTED` 뒤, `close_at`, 확정 뒤 인원) | 세션 — 러닝 DB, 메인의 `runningRoomId`가 키 | 메인은 세션 닫힘 이벤트로 사본을 고친다. '시작됨'은 `MATCHED` + 지금 ≥ `startAt`으로 본다 | 정함 (D-26) |
| `running_players` 활성 신청 자리 | 메인 | 넘긴 자리는 세션 이벤트·이탈 응답으로만 푼다 | 제안 (D-27) |
| `running_players` 러닝 상태 | 세션 (참가자, 같은 `runningPlayerId`) | 메인은 최종 상태를 이벤트로 받아 사본에 적는다 | 정함 (D-26) |
| `running_room_sessions` (자리·연결) | 확정 전 메인, 뒤 세션 | 명단은 넘기기에 실린다 | 정함 (D-26) |
| `running_room_invitations` | 메인만 — 전이가 모두 `MATCHING` 중이다. 확정 뒤 `ACCEPTED` → `LEFT`는 이탈 결과로 메인이 적는다 | 세션은 읽지 않는다. FK와 `FlushPendingChangesPort` 우회는 메인 안에 남는다 | 제안 |
| `running_room_chats` · `running_room_chat_histories` | 메인만 | 없음 — 닫힘은 시각으로 판단(「메인에 남는 흐름 · 혼자 달리기」) | 제안 |
| `running_records` · `running_segments` · `running_splits` · `running_combos` | 세션 — 러닝 DB가 정본 | 메인은 사본(구간·콤보 빼고) — 「메인 사본과 반영」 | 정함 (D-24) |
| 러닝 DB `outbox_events` · inbox · `cdc_heartbeats` | 세션 | 두 번째 커넥터·슬롯 — [platform.md](../platform.md) 「러닝 DB CDC」 | 정함 (D-24) |
| `scheduled_jobs` + 예약 채널 | 종류별 — 메인 `MATCH_CLOSE` · `RUNNING_READY` · `RUNNING_REMINDER`, 세션 `RUNNING_START` · `RUNNING_FORCE_FINISH` + 혼자 달리기 청소기 + 밀린 작업 훑기. 서비스마다 자기 테이블·채널, `(type, target_id)` 유일 | 없음 — 같이 쓰면 `RunScheduledJobHandler.executorOf`가 자기 실행기 없는 종류에 `IllegalStateException`을 던진다 | 제안 (D-29) |
| 쿨다운 (지금 `MatchCooldownRedisAdapter`) | 지키는 쪽은 메인(신청·상태가 읽는다), 정하는 쪽은 세션(페널티 판정) | 세션이 절대 시각 `cooldownUntil`을 이벤트·이탈 응답에 싣고, 메인이 `SET EXAT` 뒤 자리를 푼다(I6) | 제안 |
| `user_onboardings.avg_pace` | 메인 — inbox에서 사본의 최근 5개로 다시 계산한다(지금은 `RunningFinisher` → `UpdateUserAvgPacePort`) | 세션은 넘기기 스냅샷의 참가자 페이스로 방 평균을 낸다(I7) | 제안 |
| 몸무게 (칼로리, 지금 `LoadUserWeightPort`) | 메인 | 넘기기 스냅샷 — 끝내기가 사용자 테이블을 읽지 않아 사용자 행이 지워져도 정산된다 | 제안 |
| 프로필 (닉네임·`profile_image_key`·삭제 여부) | 메인 | 넘기기 스냅샷 + 프로필 변경·사용자 삭제 이벤트로 세션의 작은 투영. 프로필 URL을 누가 만드는지는 정하지 않음 — 후보 (가) 세션 직접 presign ([runtime.md](runtime.md)) | 제안 |
| 서명 키 · 토큰 블랙리스트 | 메인 | 「인증」 | 제안 (D-30) |
| `app_versions` (최소 버전) | 메인 | 세션 핸드셰이크 관문 — 「정하지 않은 것」 | 정하지 않음 |
| S3 원본 트랙 (`gps_track_key`) | 세션 — 쓰는 쪽 하나. 탈퇴해도 남긴다(api-spec 12-4) | 메인은 읽지 않는다 | 정함 (D-26) |
| 홈 활동 (`RunningActivityReader`) | 보여 주는 쪽은 메인 | 지금 달리는 수는 세션에서 당겨 온다(「동기 호출 허용 목록」), 끝낸 러닝은 사본 | 제안 |
| `running_rooms.deleted_at` (관리자 방 숨김) | 두 DB 모두 | 「정하지 않은 것」 | 정하지 않음 |
| `record_cards` 와 음악 아웃박스·inbox | 메인 — 그대로 | FK·잠금 대상은 그대로 — 지금 `running_records`가 사본이 된다(D-68) | 운영 중 |

## 넘기기

`정하지 않음` — 방향(명단 확정에서 넘긴다)은 정함 (D-26), 방식은 상세 설계 때 정한다.

**어떤 방식이든 넘겨야 하는 것** — 이것이 없으면 세션이 `RUNNING_START`를 허가하지 못하고 칼로리를 계산하지 못한다.

- 방 — `runningRoomId`, 종류(`MATCH` · `INVITE` · `SOLO`), `startAt`, `targetDistance`, 방 평균 페이스, 인원, 버전
- 참가자마다 — `userId`, `runningPlayerId`, 페이스, `weightKg`, 닉네임, `profileImageKey`

**여유** — 넘긴 뒤 시작까지 매칭 10분, 초대 1분, 혼자 달리기 0. 메인이 필요한 때는 시작 10분 전이지 18:00:00 정각이 아니다.

**후보** — `제안`. 방 확정 이벤트 + 당겨 오기:

1. 메인이 `closeMatching()`과 같은 트랜잭션에서 방 확정 이벤트를 아웃박스에 쓴다(혼자 달리기는 방을 만드는 트랜잭션).
2. 세션 inbox가 러닝 세션·참가자·예약 작업 둘을 러닝 DB 한 트랜잭션으로 upsert한다.
3. 세션이 모르는 방에 `RUNNING_START`·이탈·정산이 닿으면 메인에서 당겨 와(「동기 호출 허용 목록」 1) 같은 upsert를 한다. 뒤늦게 온 이벤트는 아무것도 바꾸지 않는다.
4. 당겨 오지 못하면 `RUNNING_SESSION_UNAVAILABLE`로 답해 앱이 `RUNNING_START`를 다시 보내게 한다(멱등).
5. `startAt`이 1분 안인데 세션에 행이 없는 방을 알람으로 잡는다.

- **측정 변형** — `넘기기-동기`(세션이 매번 동기로 당겨 온다) vs `넘기기-사본`(복제된 방 확정을 쓴다)을 B2에서 견준다 — [verification.md](verification.md) 「핵심 시나리오 SC1–SC7」 SC5, [rationale.md](rationale.md) 「반대 근거 AG1–AG6」 AG3.
- 이벤트 이름 후보는 「이벤트 (후보)」의 `RoomConfirmed`(대안 `RunningRoomFixed`), 토픽 이름 후보는 [platform.md](../platform.md) 「새 이름」의 넘기기 토픽.

## 이벤트 (후보)

`제안 (D-28)` — 계획된 이벤트의 단일 집이다. 본문이 정해지면 api-spec 14장으로 옮기고 여기에는 행과 링크만 남긴다. 지금 돌고 있는 이벤트는 [outbox-inbox.md](../outbox-inbox.md) 「지금 있는 이벤트」.

| 이벤트 (이름 후보) | 방향 | 언제 | 싣는 것 | 받는 쪽이 하는 일 |
|---|---|---|---|---|
| 참가자 완주 (`ParticipantFinished`) | 세션 → 메인 | 끝내기·강제 종료 확정 트랜잭션 | 방·사용자·`runningPlayerId`, 최종 상태, 끝낸 시각, 페널티, `cooldownUntil`(절대 시각), 기록 요약(`runningRecordId`·거리·시간·페이스·케이던스·칼로리·고도·시작·끝·구간 폴리라인) | 사본 upsert → 최종 상태 → 평균 페이스 → 쿨다운 → 자리 풀기 |
| 참가자 이탈 (`ParticipantLeft`) | 세션 → 메인 | 노쇼, 확정 뒤 이탈 | 방·사용자, 최종 상태, `cooldownUntil` | 쿨다운 → 자리 풀기, 초대 `LEFT` |
| 세션 닫힘 (`RunSessionClosed`) | 세션 → 메인 | 마지막 참가자가 끝났을 때·강제 종료 | 방 상태, `closeAt`, 참가자 최종 상태 | 방 사본 상태 |
| 세션 시작 (`RunSessionStarted`) — 선택 | 세션 → 메인 | 방 `STARTED` | 방, 시각 | 없어도 된다 — '시작됨'은 시각으로 본다 |
| 방 확정 (`RoomConfirmed`) | 메인 → 세션 | 「넘기기」 후보 | 「넘기기」의 넘겨야 하는 것 | 러닝 세션·참가자·예약 upsert |
| 사용자 삭제 (`UserDeleted`) | 메인 → 세션 | 탈퇴 saga 끝 | 사용자 | 프로필 투영에 삭제 표시, 모든 인스턴스의 소켓 닫기 |
| 프로필 변경 (`UserProfileChanged`) | 메인 → 세션 | 닉네임·프로필 사진 변경 | 사용자, 닉네임, `profileImageKey` | 프로필 투영 |
| 토큰 폐기 (`TokenRevoked`) — 후보 | 메인 → 세션 | 로그아웃·탈퇴 | 「인증」에서 정한다 | 세션 폐기 캐시 |
| 관리자 방 숨김 (`AdminRoomHidden`) — 후보 | 메인 → 세션 | 관리자 숨김 | 방 | 「정하지 않은 것」 |

- **결과 이벤트를 하나로 묶지 않는다** — 노쇼·확정 뒤 이탈과 방 닫힘을 한 모양에 담지 못한다(D-28).
- **key는 방 ID다** — 같은 방의 완주들과 닫힘이 같은 파티션에서 순서대로 온다. 사용자 생애 이벤트는 방이 없어 사용자 ID로 둔다(제안).
- **파티션 수는 토픽을 만들기 전에 정한다** — 나중에 늘리면 같은 방의 순서가 깨진다. 이름·파티션은 [platform.md](../platform.md) 「새 이름」.
- **구간 기록(splits)은 싣지 않는다** — 메인은 구간을 쓰지 않고(6-2는 세션이 응답) 10km면 구간 행이 천 개 안팎이다. 결과 한 건 크기는 SC6 2부(`K-SC6-3`)에서 잰다.
- **계약은 더하기만 하고 버전을 붙인다** — 저장소가 둘이라 계약 테스트와 공개 픽스처를 둔다(D-47). 이벤트마다 [outbox-inbox.md](../outbox-inbox.md) 「새 이벤트를 더할 때」를 따른다.

## 메인 사본과 반영

메인이 사본을 갖는 것은 `정함 (D-24)`. 사본의 모양은 `정함 (D-68)` — 지금 테이블 그대로, 반영 순서는 `제안`, 날씨는 세션이 끝낼 때 잠금 밖에서 조회해 기록에 넣는다 — `정함 (D-63)`.

**사본에 있어야 하는 것** — 모양이 무엇이든 이것을 채운다.

| 쓰는 곳 | 필요한 것 | 지금 읽는 코드 |
|---|---|---|
| 8-1 기록 카드 상세 | 기록 요약, 구간 폴리라인, 함께 뛴 사람(시작한 참가자) | `RecordCardPersistenceAdapter` |
| 9-1 ~ 9-6 카드 쓰기 | 소유·숨김 확인과 잠금 대상 | `LockOwnRunningRecordPort.lockOwnedBy` · `ExistsOwnRunningRecordPort` |
| 9-5 `running_summary` | 거리, 움직인 시간, 평균 페이스, 케이던스, 시작 시각, 날씨 코드, 기온, 누적 상승 고도 | `LoadRunningSummaryPort` — [ai-server/architecture.md](../ai-server/architecture.md) 「러닝 세션 분리의 영향」 |
| 7-1 기록 목록 | 기록 요약 + 카드 `activityType` (메인 안 조인) | `LoadMyRunningRecordsPort` |
| 함께 뛴 사람 추천 | 방 참가자와 최종 상태 | `FriendRecommendationPersistenceAdapter.loadRanTogether` |
| 홈 활동 | 최근 끝낸 러닝, 오늘·어제 완주자 | `RunningActivityReader` |
| 평균 페이스 | 최근 5개 기록의 페이스 | `LoadRecentRunningPacesPort` |
| 상태 · 채팅 · 초대 | 방 상태와 참가자 최종 상태 | `LoadUserStatusPort` |

구간(`running_splits`)과 콤보는 두지 않는다 — 6-2로 세션이 응답한다.

**모양** — `정함 (D-68)`: 지금 `running_records`(와 세그먼트)를 사본으로 쓴다. `record_cards.running_record_id` FK(`fk_record_card_running_record`, `ON DELETE CASCADE`)와 `lockOwnedBy`의 `PESSIMISTIC_WRITE`가 그대로다. 카드 FK와 잠금은 메인 사본 행을 가리킨다 — DB를 넘는 FK·잠금은 없다.

**반영 순서** — `제안`. 메시지 하나에 메인 트랜잭션 하나다.

1. inbox 기록 (`(topic, event_id)` 충돌이면 이미 처리 — 끝)
2. 사본 upsert
3. 참가자 최종 상태를 사본에 적는다
4. 평균 페이스를 다시 계산한다(사본의 최근 5개)
5. 쿨다운 `SET EXAT` (절대 시각이라 다시 와도 같다)
6. 자리를 푼다(`running_players.deleted_at`)
7. 커밋 → 오프셋 커밋

- **핸들러 안에서 외부 호출을 하지 않는다** — 날씨·S3를 부르면 한 파티션의 직렬 처리라 끝내기 몰림이 몇 분짜리 반영 지연이 된다.
- **지금 inbox를 그대로 쓴다** — `RecordInboxEventPort` · `InboxEventAdapter` · `KafkaConsumerConfig`의 재시도 기준. 다만 오류 처리기가 빈 하나라 새 리스너도 건너뛰기를 물려받는다 — 결과 이벤트를 받기 전에 DLT·건너뜀 알람·대조를 만든다(D-51, [outbox-inbox.md](../outbox-inbox.md) 「받는 쪽 — Inbox」).
- **반영 전에는 보이지 않을 뿐이다** — 7-1·카드에는 반영된 뒤 나온다(D-58). 반영 전 사용자가 활성으로 보이는 창은 [consistency.md](consistency.md) 「종료 뒤 최종 일관 창」, 누락 0 확인은 「대조」.

**날씨** — `정함 (D-63)`. 세션이 끝내기 봉인(잠금 밖)에서 지금과 같은 기준(출발 좌표 · 출발 시각, 실패하면 기본값)으로 조회해 기록에 넣고, 메인은 사본으로 받는다. 값이 지금과 같아 9-5 `running_summary`의 날씨 필드와 erd는 바뀌지 않는다.

## 동기 호출 허용 목록

방식은 `정함 (D-52)` — 서비스 사이는 Kafka로만 주고받고([README.md](../README.md) 「통신 — 어디로 무엇이 지나가는가」), 예외는 이 표에 적힌 호출뿐이다. 목록 밖 호출은 금지이고 `check_conventions.py`로 막는다(D-54). 호출 후보와 공통 규칙은 `제안 (D-53)`. 경로·본문은 정하면 api-spec 14장.

| # | 호출 | 방향 | 언제만 | 시간 제한 | 멱등 | 실패하면 | 상태 |
|---|---|---|---|---|---|---|---|
| 1 | 넘기기 당겨 오기 | 세션 → 메인 | 모르는 방에 `RUNNING_START`·이탈·정산이 닿을 때 | 500ms, 재시도 1회, 회로 차단 | 넘기기와 같은 upsert | `RUNNING_SESSION_UNAVAILABLE` → 앱이 다시 보낸다 | 제안 — 「넘기기」와 함께 정한다 |
| 2 | 탈퇴 정산 | 메인 → 세션 | 러닝 중 탈퇴 saga의 기점 | 3초 (D-70) | 사용자 ID + 탈퇴 ID | 기점 전 실패 → 지금의 503 `ACCOUNT_DELETION_UNAVAILABLE` | 정함 (D-44, D-70) — [consistency.md](consistency.md) 「계정 탈퇴 saga」 |
| 3 | 확정 뒤 이탈 전달 | 메인 → 세션 | 넘긴 신청에 `POST /match-rooms/leave` | — | 방 ID + 사용자 ID | 세션에 닿지 못하면 상태를 바꾸지 않고 실패 | 후보 — [consistency.md](consistency.md) 「확정 뒤 이탈」이 정하지 않음 |
| 4 | 활성 확인 | 메인 → 세션 | `/users/me/status`가 `RUNNING`을 계산할 때만 | 300ms | 읽기 | 메인 자기 판단 | 제안 |
| 5 | 지금 달리는 수 | 메인 → 세션 | 홈 활동 방송 주기 | 짧게 | 읽기 | 마지막 값 | 제안 |
| 6 | 대조 당겨 오기 | 메인 → 세션 | 넘긴 신청이 시작 + 6시간(`match.force-finish-offset`) + 여유를 지나도 열려 있을 때 | 배치 | 반영은 inbox와 같은 처리 | 다음 주기 | 제안 — [consistency.md](consistency.md) 「대조」 |

결과 조회의 동기 대체는 목록에 없다 — 세션이 직접 응답한다(D-58).

**공통 규칙** — `제안 (D-53)`

- **내부 포트로만 부른다** — 포트 값은 [migration.md](migration.md) 「선행 조건」. ALB는 내부 경로를 404로 막는다(「공개 경로」).
- **서비스 인증을 건다** — 방식은 정하지 않음.
- **시간 제한은 앱이 기다리는 시간보다 짧다** — 멱등 키와 회로 차단을 둔다. 상관 ID는 OTel 에이전트(D-55)가 잇는다.
- **호출하는 동안 메인 DB 잠금을 쥐지 않는다**.
- **18:00 시작 경로에서 부르지 않는다** — 1번도 모르는 방일 때만이다. WS 작업 스레드를 긴 호출로 막지 않는다.

## 메인에 남는 흐름 · 혼자 달리기

`제안` — 아래는 메인 안에서 끝나고 세션을 부르지 않는다. 예약은 `MATCH_CLOSE` · `RUNNING_READY` · `RUNNING_REMINDER`만 건다(D-29).

| 흐름 | 분리 뒤 |
|---|---|
| 매칭 신청 · 초대 열기 · 보내기 · 수락 · 거절 | 그대로. 반영 전 창에는 `MATCH_ALREADY_IN_PROGRESS`·초대 대상 '바쁨'이 날 수 있다(D-31) — 다시 신청할 수 있기까지의 시간을 잰다([verification.md](verification.md) 「지표」) |
| 넘기기 전 나가기 | 그대로 메인 안 |
| 대기방 채팅 | `MATCHED`이고 지금 ≥ `startAt`이면 닫힌 것으로 본다(`RoomChatAccessPolicy`) — `StartRunningHandler`가 `startAt` 전 시작을 막으므로 이벤트가 필요 없다 |
| `/users/me/status` | 반영 전 `RUNNING`을 줄 수 있다. `RUNNING`을 계산할 때만 세션에 활성 확인(허용 목록 4)을 하고, 아니면 `IDLE`. 끝낸 참가자의 `RUNNING_START`는 계속 `NOT_ROOM_PLAYER`다 |
| 홈 활동 | 지금 달리는 수는 허용 목록 5, 끝낸 러닝은 사본 |
| 매칭 스트림 `RUNNING_READY` | 메인 SSE 그대로 — 「정하지 않은 것」 6 |

**혼자 달리기**

- **`POST /running-rooms/solo`는 메인에 남는다** — 메인이 번호를 매기고 신청과 `MATCHED`로 태어난 `SOLO` 방을 한 트랜잭션에 만든다. 지금처럼 예약 작업은 없다(`OpenSoloRoomHandler`).
- **넘기기 여유가 0이다** — 바로 이어지는 `RUNNING_START`가 모르는 방에 닿으므로 넘기기 방식이 무엇이든 당겨 오기(허용 목록 1)가 이 경로를 받친다.
- **세션에 혼자 달리기 청소기를 둔다** — 지금은 앱이 죽으면 `RUNNING`이 영영 남고(`RunningActivityReader` 주석), Redis 트랙은 7시간(`running-track.ttl`)에 조용히 사라진다. 분리 뒤에는 메인 자리까지 계속 막힌다. 청소기의 끝내기는 [runtime.md](runtime.md), 혼자 달리기 이탈·탈퇴는 [consistency.md](consistency.md).

## 결과 조회

`정함 (D-58)`

- **6-1 `GET /running-rooms/{runningRoomId}/results`와 6-2 `…/split-results`는 세션이 응답한다** — ALB 경로 규칙(「공개 경로」)으로 보내 공개 주소와 계약이 그대로다. 자기 기록은 ack 전에 러닝 DB에 커밋됐으므로 끝낸 직후 읽어도 보인다.
- **세션이 프로필을 스스로 채운다** — 지금 `GetRunningResultsHandler`·`GetRunningSplitResultsHandler`는 `LoadPlayerProfilesPort`·`GenerateViewUrlPort`로 메인 사용자 데이터를 읽는다. 분리 뒤에는 세션의 프로필 투영(「데이터 주인」)에서 닉네임·삭제 여부를 만든다(프로필 URL은 정하지 않음). 사용자 자산 버킷 읽기 권한은 [platform.md](../platform.md) 「S3」.
- **메인의 7-1·기록 카드는 사본에 반영된 뒤 보인다** — 카드 생성은 경합하지 않는다. `runningRecordId`는 메인이 사본으로 내는 응답(7-1 · 홈 활동 · 8-1)으로만 앱에 가고 6-1 · 6-2에는 없다.
- **복제는 대조로 확인한다** — 러닝 DB와 메인 사본의 누락 0(I5) — [consistency.md](consistency.md) 「대조」.
- 분리 전에 끝난 방의 6-1·6-2를 어디서 응답할지는 [migration.md](migration.md) 「옛 기록」.

## 인증

`제안 (D-30)` — 폐기된 토큰을 세션이 아는 방법은 `정하지 않음`.

- **지금** — 메인이 HS256 비밀값 하나로 서명하고(`JwtTokenAdapter`) 검증한다(`JwtDecoderConfig` — issuer·audience·`BlockedTokenValidator`). 이 비밀값을 가진 서버는 토큰을 만들 수 있다.
- **목표** — 메인이 ES256으로 서명하고 세션은 공개 키로 로컬에서 검증만 한다. 공개 키는 Parameter Store로 나눠 주고, 실행 중 메인 JWKS 호출은 하지 않는다(핸드셰이크가 메인에 동기로 기대게 된다).
- **두 키를 함께 받는 기간을 둔다** — 이미 나간 앱의 토큰이 만료될 때까지. 서버만 바뀌고 앱 계약은 그대로다.
- **그 전까지 임시안** — 끝날 날짜를 정하고, 세션은 HS256 비밀값으로 검증만 하고 메인 블랙리스트를 읽기만 한다.
- 위치·비밀값 가두기 근거(G7)는 ES256 뒤에만 선다 — [rationale.md](rationale.md) 「받쳐 주는 근거 G5–G8」.

## 공개 계약 영향

요청 없이 공개 계약을 바꾸지 않는다. 앱에 보이는 변경은 iOS·안드로이드 최소 버전 상향과 묶는다.

| 경로 | 분리 뒤 | 상태 |
|---|---|---|
| WS `/api/v1/ws/running` | **그대로** — 같은 JWT·`X-App-Version`·메시지·오류 코드, ALB가 세션으로 보낸다 | 정함 (D-26) |
| 6-1 · 6-2 | **그대로** — 세션이 응답 | 정함 (D-58) |
| `POST /running-rooms/solo` | **그대로** — 메인 | 제안 (D-27) |
| `DELETE /users/me` (12-4) | **그대로** — 정산 실패는 지금의 503 `ACCOUNT_DELETION_UNAVAILABLE` | 정함 (D-43) · 단계는 제안 (D-44) |
| `/running-matches` (legacy) | **그대로** — ALB 404, 메인 | 운영 중 |
| `RUNNING_START` 오류 `RUNNING_SESSION_UNAVAILABLE` | 모양 그대로, 원인 하나 더(넘기기를 당겨 오지 못함) — 앱은 지금처럼 다시 보낸다 | 제안 |
| `/users/me/status` (2-2) | 모양 그대로, 뜻이 바뀜 — 끝낸 뒤 반영 전 `RUNNING`, `cooldownUntil`이 늦게 보인다 | 제안 (D-31) |
| 매칭 신청 · 초대 수락 · 혼자 달리기 | 모양 그대로, 반영 전 409가 날 수 있다 | 제안 (D-31) |
| 7-1 · 홈 활동 · 친구 추천 | 모양 그대로, 반영 뒤 보인다 — api-spec 7-1에 메모를 단다(전환 때, [migration.md](migration.md) 「단계마다 고칠 문서」) | 정함 (D-58) |
| 9-1 ~ 9-6 · 9-5 이벤트 | 본문 그대로 — `weather_code`는 지금처럼 기록에 이미 있다 (D-63) | 정함 |
| `POST /match-rooms/leave` | 경로·2xx·409 그대로. (가)를 고르면 넘긴 방에서 세션에 닿지 못할 때 **새 실패**가 생긴다 — (나)면 없다 — 오류 코드를 정하면 도메인별 `*ErrorCode` enum·`GlobalExceptionHandler.toStatus()`·`ErrorExposurePolicy.EXPOSED_CODES`에 함께 넣는다 | 정하지 않음 ([consistency.md](consistency.md) 「확정 뒤 이탈」) |
| JWT HS256 → ES256 | 서버만 — 두 키 기간 | 제안 (D-30) |
| `RUNNING_READY` (5-C) | 메인 SSE 그대로 | 정하지 않음 (「정하지 않은 것」 6) |

## 공개 경로

ALB 경로 규칙의 단일 집이다. 순위 1 · 2 · 10은 운영 중인 규칙이고, 새 규칙의 순위 값은 `제안`이다. EKS IngressGroup 순서는 [eks.md](../eks.md) 「엣지 · 자동 확장 · 배포」.

| 순위 | 경로 | 동작 | 상태 |
|---|---|---|---|
| 1 | `/api/v1/actuator/health` | 메인 대상 그룹 | 운영 중 |
| 2 | `/api/v1/actuator`, `/api/v1/actuator/*` | 고정 403 | 운영 중 |
| 3 | `/api/v1/internal`, `/api/v1/internal/*` | 고정 404 | 제안 (D-53) |
| 10 | `/api/v1/running-matches`, `/api/v1/running-matches/*` | 고정 404 (JSON `NOT_FOUND`) | 운영 중 |
| 20 | `/api/v1/ws/running` | 세션 대상 그룹 | 정함 (D-26) |
| 21 | `/api/v1/running-rooms/*/results`, `/api/v1/running-rooms/*/split-results` | 세션 대상 그룹 | 정함 (D-58) |
| 나머지 | 그 밖의 `/api/v1/*` | 메인 대상 그룹 | 운영 중 (지금은 앱 하나) |

- **세션 규칙을 `/api/v1/running-rooms/*`로 넓히지 않는다** — 넓히면 `/running-rooms/solo`가 세션으로 간다. 넓혀야 하면 solo 규칙을 더 앞 순위로 메인에 둔다.
- **메인은 REST + SSE만, WS는 세션만** 받는다.
- **대가** — 주소 배치가 서비스 주인에 묶인다. 받아들였다(D-58).
- 엔진을 방 단위로 고르는 라우팅(ALB 가중치 금지)은 옮기는 동안의 일이라 [migration.md](migration.md) 「라우팅」.

## 정하지 않은 것

1. **넘기기 방식** — 후보는 방 확정 이벤트 + 당겨 오기(「넘기기」). 이름, 여유 10분 · 1분 · 0을 CDC 지연이 감당하는지(SC5)와 함께 정한다 (D-26).
2. ~~메인 사본 모양~~ — **정했다 → D-68**: 지금 `running_records` · `running_segments`를 그대로 쓴다.
3. ~~날씨 자리~~ — **정했다 → D-63**: 세션이 끝낼 때 잠금 밖에서 조회한다.
4. **세션 핸드셰이크의 최소 버전 관문** — 지금 `AppVersionFilter`는 WS 업그레이드도 막고(`shouldNotFilter`가 빼는 것은 앱 버전·actuator·관리자·preflight뿐) 요청마다 `app_versions`를 캐시 없이 읽는다. (가) 세션에 복제(이벤트·주기 당겨 오기) (나) 공유 라이브러리 (다) 스플래시(2-3)에서 메인이 이미 막은 것에 기댄다고 명시.
5. **폐기된 토큰** — 서비스마다 Redis가 하나라(D-49) 세션이 메인 블랙리스트를 바로 못 읽는다. (가) 메인 블랙리스트 키 읽기 전용 계약(저장소 사이 계약 테스트) (나) 짧은 access 토큰 수명 (다) 토큰 폐기·사용자 삭제 이벤트로 세션 자기 폐기 캐시. ES256과 함께 정한다 (D-30).
6. **`RUNNING_READY`가 메인 SSE를 탄다** — 시작 전 메인에 기대는 마지막 고리다. 앱은 `RUNNING_READY` 없이도 기기 시각으로 `RUNNING_START`를 보내므로(api-spec 5-C) 메인이 멈춰도 시작은 되지만 카운트다운 맞추기가 빠진다. WS로 옮기면 계약 변경이라 두 플랫폼 최소 버전 상향이 필요하다.
7. **관리자 방 숨김** — `running_rooms.deleted_at`(손 SQL)이 7-1·함께 뛴 사람·카드 잠금을 거른다. 분리 뒤 두 DB에 있다: 두 DB를 모두 고치는 절차 vs 관리자 방 숨김 이벤트.
8. **결과 이벤트 계약이 저장소를 나눈 뒤 사는 곳** — 메인 저장소의 api-spec 14장 하나에 두고 세션 저장소는 공개 픽스처로 따를지, 양쪽에 둘지.
