# Runiverse ERD (러너버스 데이터 모델)

> 테이블·컬럼명은 PostgreSQL 표준 소문자 `snake_case`. API 표면은 `camelCase`로 매핑(백엔드 담당). 테이블명은 복수형(`users`·`feeds`·`comments` …), FK 컬럼은 참조 테이블의 단수 PK명 그대로 유지(`user_id`·`feed_id`). 자바 엔티티 클래스는 한 행을 표현하므로 단수(`UserJpaEntity`).

---

## 0. 공통 규칙

- **PK 타입**: `users.user_id`만 **UUID**, 그 외 자체 PK는 **bigint**(auto-increment). 연결·좋아요류(`friendships`·`user_colors`·`feed_likes`·`comment_likes`·`running_room_sessions`)는 **복합 PK**, 유저당 1 row(`user_onboardings`·`oauth_users`·`delete_users`)는 **참조 키가 곧 PK**. → API: `userId`만 UUID 문자열, 나머지 Long.
- **FK/참조 네이밍**: 참조 테이블 PK명 그대로(예: `running_records.running_room_id`). 같은 테이블 이중 참조는 역할명(`friendships.requester_id`/`receiver_id`). `feeds.running_record_id`는 논리 참조(아래 정책).
- **UNIQUE 표기**: 단일 컬럼 = 제약칸, 복합 UNIQUE = 표 아래 블록쿼트(`oauth_users`·`running_records`·`running_splits`·`colors`).
- **타임스탬프**: **접미사가 타입을 말한다** — 시점은 전부 `*_at`(`timestamp`, 시간대 없음, **KST 벽시계로 저장**). 앱이 JVM 기본 타임존을 `APP_TIME_ZONE`으로 고정한다(`TimeZoneConfig`). 달력 날짜(`date`, API `YYYY-MM-DD`)는 `user_onboardings.birthday` 하나뿐인 예외다.
- **감사 컬럼**: `created_at`·`updated_at`은 `NOT NULL`, 앱이 자동 세팅(Hibernate `@CreationTimestamp`/`@UpdateTimestamp`). **write-once 테이블은 `created_at`만 둔다**(`running_records`·`running_splits`·`feed_images`·좋아요류·`user_colors`) — 고치지 않으므로 `updated_at`이 늘 같은 값이다. 엔티티는 `BaseCreatedAtEntity`를 상속한다.
- **지표 컬럼 접두어**: 실적 합계는 `total_*`(`total_distance`·`total_duration`·`total_calories`·`total_elevation_gain`), 평균은 `avg_*`(`avg_pace`·`avg_cadence`), 목표는 `target_*`(`target_distance`). **구간(`running_splits`)은 부분값이라 접두어 없이 적는다**(`distance`·`duration`·`calories`) — 접두어의 유무가 전체와 구간을 가른다. 개수 컬럼은 `*_count`(`max_member_count`·`current_member_count`·`desired_member_count`·`leave_count`·`like_count`·`comment_count`)로 예외가 없다.
- **컬럼 순서**: `PK → FK → 분류·상태 → 조건·속성 → 결과·이력 → 감사 컬럼` 순으로 적는다. **PK와 FK는 붙여 쓰고**, FK가 여럿이면 상위 엔티티부터(`running_room_id` → `user_id`). `created_at`·`updated_at`·`deleted_at`은 **항상 맨 아래**다.
- **단위(컬럼에 단위 미표기 — 아래로 통일)**: 거리 = **미터**, 페이스(`avg_pace`) = **초/km**, 시간(`total_duration`·`duration`) = **초**, 칼로리 = **kcal**, 케이던스(`avg_cadence`) = **spm**, 누적 상승 고도(`total_elevation_gain`)·구간 순고도차(`elevation_change`) = **미터**, 기온(`temperature`) = **섭씨**. **좌표는 컬럼으로 두지 않는다** — 경로·지점은 전부 `route_polyline`(encoded polyline, precision 5)에서 뽑는다. PostGIS 미사용(위치 기반 기능 도입 시 검토).
- **enum**: DB도 API와 **동일한 영문 코드를 그대로 저장**(Java enum `@Enumerated(STRING)`) — 한글 값·변환 매핑 없음. 컬럼별 값 목록은 [§6 enum 사전](#6-enum-사전).
- **소프트 삭제**: `deleted_at`(nullable)이 있는 테이블(`feeds`·`comments`·`running_rooms`)은 소프트 삭제. `delete_*` 테이블은 별도 용도([§5](#5-delete_-스냅샷이력-테이블)).
  - **`running_players.deleted_at`은 예외 — 숨김 표시가 아니라 "신청이 끝난 시각"이다**(대기 취소·초대 거절·이탈 공통, 완주는 정상 종료라 null). 조회에서 걸러내는 데도 쓰지만(활성 신청 판정) 본래 용도는 **쿨다운 판정**이라 값이 시각 자체로 의미를 갖는다. 컬럼명은 다른 테이블과 맞추되 의미가 다르다.
- **`user_id` FK 정책 (회원탈퇴 연동)**: 탈퇴 시 **CASCADE 삭제**되는 테이블(`user_onboardings`·`oauth_users`·`user_devices`·`friendships`·`user_colors`)은 `user_id` **FK + ON DELETE CASCADE**. **유지**되는 테이블(`feeds`·`comments`·`running_records`·`feed_likes`·`comment_likes`)은 `user_id`를 **논리 참조**(FK 제약 없음 — `users` 하드delete 후 값 유지, 무결성은 앱 레벨). 표기 `→ users`
  - **`running_players`는 둘 중 어느 쪽도 아니다** — `user_id`가 논리 참조인데 탈퇴 시에는 삭제 대상이다. FK가 없어 DB가 지워주지 않으므로 탈퇴 유스케이스에서 **명시적으로 DELETE**한다(딸린 `running_room_sessions`는 `running_player_id` FK의 CASCADE로 함께 삭제).
  - 논리 참조 컬럼은 전부 **NOT NULL**이다(nullable은 스냅샷 테이블 `delete_feeds`·`delete_comments`뿐).
- **`feeds.running_record_id` 참조 정책**: 별개 애그리거트라 하드 FK 없이 **ID로만 논리 참조**(DDD *Reference by Identity*). 표기 `→ running_records`. **무결성은 앱 레벨**: 저장 시 존재 검증, 조회 시 유령 참조 방어(기록 카드 미표시).

---

## 1. 도메인 A — 유저 · 인증

### users

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK | |
| email | varchar | UNIQUE, NOT NULL | 로컬·소셜 공통 |
| password_hash | varchar | nullable | 소셜 전용 유저는 null. 원문 미보관 |
| alert_consent | boolean | NOT NULL, default true | 전체 알림 on/off 단일 토글 — 모든 푸시 관장 (설정 13-3/13-4). 거래성 알림뿐이라 **기본 on** |
| profile_visibility | enum | NOT NULL, default PUBLIC | 지인 마스킹 on/off |
| profile_image_key | varchar | nullable | S3 key(Presigned 업로드). 미등록이면 null |
| introduction | varchar | nullable | 소개글 |
| created_at / updated_at | timestamp | NOT NULL | |

### user_onboardings

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK, FK → users | 참조 키가 곧 PK — 1:1 강제 (온보딩 1 row) |
| nickname | varchar | UNIQUE, NOT NULL | 중복 시 409. 프로필 표시명(닉네임 변경도 이 컬럼 갱신) |
| gender | enum | NOT NULL |  |
| birthday | date | NOT NULL | |
| avg_pace | int | NOT NULL | 초/km. 온보딩 입력이 초기값 → 이후 서버가 러닝 기록 기반 자동 갱신 |
| weight | numeric(4,1) | NOT NULL | kg |
| height | numeric(4,1) | NOT NULL | cm |
| created_at / updated_at | timestamp | NOT NULL | created_at = 온보딩 완료 시각 |

> `users`=계정/인증, `user_onboardings`=온보딩 프로필(온보딩 완료 = row 존재). 조회 시 eager fetch 권장.

### oauth_users

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK, FK → users | 참조 키가 곧 PK — 유저당 소셜 1개(1:1 확정) |
| provider | enum | NOT NULL |  |
| provider_id | varchar | NOT NULL | provider 내 유저 식별자 |
| created_at / updated_at | timestamp | NOT NULL | |

> UNIQUE (provider, provider_id) — 같은 소셜 계정 중복 연결 방지.

### user_devices

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_device_id | bigint | PK | |
| user_id | UUID | FK → users, NOT NULL | |
| push_token | varchar | NOT NULL | FCM/APNs 토큰 |
| platform | enum | NOT NULL |  |
| device_id | varchar | UNIQUE, NOT NULL | 기기 식별자 (`POST /devices` upsert 키) |
| app_version | varchar | nullable | |
| is_active | boolean | NOT NULL, default true | 재로그인 시 devices API가 true 갱신. 기기 단위 비활성화(로그아웃 시 false)는 **[MVP 제외]** — 로그아웃은 토큰 블랙리스트만 |
| created_at / updated_at | timestamp | NOT NULL | |

---

## 2. 도메인 B — 매칭 · 러닝

### running_rooms

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_room_id | bigint | PK | API `runningRoomId`(Long)가 이 값을 가리킴. **신청·개시 즉시 1인 방으로 생성** — 매칭은 `MATCHING`, 솔로는 `STARTED`로 시작한다 |
| type | enum | NOT NULL | `SOLO` / `MATCH` / `INVITE` — [§6](#6-enum-사전). 생성 시 확정·불변. 매칭 후보 스캔·대기 인원 집계가 `type='MATCH'`만 보므로 솔로 방과 초대방이 섞이지 않는다 |
| status | enum | NOT NULL, default MATCHING | 진행 단계 — [§6 enum 사전](#6-enum-사전) |
| start_at | timestamp | NOT NULL | 예약 시작 시각 |
| close_at | timestamp | nullable | 모집 마감 시각(`start_at - 설정값`). **생성 시 고정** — 설정을 바꿔도 진행 중인 방의 마감이 움직이지 않고, 마감 스케줄러(`type='MATCH' AND status='MATCHING' AND close_at <= now()`)가 계산식 대신 컬럼을 봐야 인덱스를 탄다. 모집 단계가 없는 솔로는 null |
| target_distance | int | nullable | 방의 목표 거리(미터). 매칭 조건이라 **정해진 뒤에는 바뀌지 않는다**. 참가자에게서 유추하지 않고 방이 직접 가져 후보 방 조회가 단일 테이블에서 끝난다 |
| avg_pace | int | nullable | 참가자 평균 페이스(초/km). 참가·이탈마다 갱신. 배정 시 페이스가 가까운 방을 고르는 데 쓰고, `RoomInfo.teamAveragePaceSecondsPerKm`로도 나간다 |
| max_member_count | int | NOT NULL | 자리 수 — 매칭 `4`, 솔로 `1`. **생성 시 확정·불변** |
| current_member_count | int | NOT NULL | 현재 인원. 생성 시 `1`, 참가·이탈마다 갱신. `current_member_count < max_member_count`면 들어갈 수 있다 |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable | **[MVP 제외]** 관리자 부정 방 숨김용 |

### running_players

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_player_id | bigint | PK | 매칭 요청 = 이 row |
| user_id | UUID | → users, NOT NULL | 논리 참조(FK 제약 없음). 탈퇴 시 앱이 명시적으로 삭제 — [§0](#0-공통-규칙) |
| status | enum | NOT NULL, default JOINED | 참가·진행 상태 — [§6 enum 사전](#6-enum-사전) |
| start_at | timestamp | NOT NULL | 희망 시작 시각 |
| target_distance | int | NOT NULL | 목표 거리(미터, API `targetDistanceMeters`). **목표는 `target_*`, 실적은 `total_*`** — `running_records.total_distance`(실제 이동 거리)와 이름으로 갈린다 |
| avg_pace | int | NOT NULL | 신청 시점의 사용자 평균 페이스(초/km). **입력받지 않는다** — 매칭 조건에 페이스 항목이 없어(5-A) 서버가 `user_onboardings.avg_pace`에서 복사한다. 배정 시 방 평균과의 근접도 판정에 쓴다 |
| desired_member_count | int | nullable | **[MVP 제외]** 유저 희망 매칭 인원 — 서버가 2~4명으로 자동 편성 |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable | **신청이 끝난 시각** — 대기 취소·초대 거절·이탈 공통. 한 번 찍히면 바뀌지 않는다 |

> **방과의 연결은 `running_room_sessions`가 갖는다** — `running_players`는 "매칭 신청" 단위다. 참가자가 재배정·병합으로 여러 방을 거칠 수 있어 단일 `running_room_id` 컬럼으로는 이력을 담을 수 없고, 현재 속한 방은 `is_current`로 가린다.
> **`status`는 참가 의사와 진행 상태를 함께 표현한다** — 신청(`JOINED`)·초대(`INVITED`)에서 러닝(`RUNNING`)·완주(`COMPLETED`)까지 한 축으로 간다. 이탈은 시점(확정 후 / 러닝 중)과 제재 여부로 네 값이 갈린다.
> **`status`와 `deleted_at`은 축이 다르다** — `status`가 "어떻게 끝났나"(사유·제재 여부), `deleted_at`이 "언제 끝났나"다. `updated_at`을 이탈 시각으로 쓰지 않는 이유는 그 row가 한 번만 더 갱신돼도 값이 밀려 쿨다운이 잘못 계산되기 때문이다. 쿨다운 판정: `status IN ('MATCHED_LEFT_PENALTY','RUNNING_LEFT_PENALTY')`이면서 `deleted_at`이 쿨다운 안이면 재신청을 막는다.
> **row 생명주기**: 생성 = 매칭 신청·솔로 개시·초대 발송(`INVITED`) / 대기 취소·초대 거절 = `deleted_at` 기록 / 이탈 = `status=*_LEFT_*` + `deleted_at` 기록 / 완주 = `status=COMPLETED`, `deleted_at`은 null(정상 종료라 삭제가 아니다) / 방 자동 취소 = 전원 유지(방 `status`만 `CANCELLED`).
> **활성 신청 판정**: `deleted_at IS NULL AND status='JOINED'`. `INVITED`는 활성으로 치지 않는다.

### running_room_sessions (참가자 ↔ 방 배정)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_room_id | bigint | PK1, FK → running_rooms | 배정된 방 |
| running_player_id | bigint | PK2, FK → running_players, ON DELETE CASCADE | |
| leave_count | int | NOT NULL, default 0 | 이 방에서 이탈한 **누적** 횟수. 참가자는 매칭 과정에서 여러 방을 옮겨 다니고 **같은 방으로 돌아올 수도 있어** 2 이상이 된다(복합 PK라 row는 그대로 두고 이 값만 오른다). 배정 시 **이 값이 낮은 방을 우선 연결한다** — 사람들이 잘 떠나지 않은 방이 매칭 품질이 좋다는 신호다 |
| is_current | boolean | NOT NULL, default true | 현재 소속 여부. 복합 PK로 참여 이력이 쌓이므로, 지금 속한 방은 이 값으로 가린다. **한 참가자의 행 중 정확히 하나만 true이고 나머지는 전부 false다** — 값이 없는 배정은 성립하지 않으므로 nullable로 두지 않는다(nullable이면 "떠난 방"과 "아직 안 정해진 방"이 구분되지 않는다) |
| created_at / updated_at | timestamp | NOT NULL | `updated_at` = 마지막 배정 변동 시각(`is_current` 전환·`leave_count` 증가). **write-once가 아니라 두 컬럼 다 둔다** — 재배정·복귀로 갱신되는 테이블이다 |

> **복합 PK가 참여 이력을 만든다** — 한 참가자가 새 방으로 옮기면 row가 하나 더 쌓이고, 이전 방 row는 `is_current=false`로 남는다. 어느 방을 거쳤는지가 그대로 이력이다.
> **거쳐 간 방으로 되돌아오면 row를 새로 만들지 않는다** — 복합 PK가 같으므로 기존 행의 `is_current`를 다시 true로 돌리고 `leave_count`만 누적된다. 그래서 "몇 번 거쳤나"가 아니라 "몇 번 떠났나"가 남는다.
> `running_rooms.current_member_count`는 방 이동 시 두 방이 한 트랜잭션에서 같이 갱신된다.

### running_records

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_record_id | bigint | PK | |
| running_room_id | bigint | FK → running_rooms, NOT NULL | 솔로 러닝도 방을 만드므로 항상 값이 있다 |
| user_id | UUID | → users, NOT NULL | |
| avg_pace | int | NOT NULL | 초/km |
| total_distance | int | NOT NULL | 미터 |
| total_duration | int | NOT NULL | 초. 구간(`running_splits.duration`)의 합. **일시정지 시간은 빠진다** — 멈춘 동안은 어느 구간에도 쌓이지 않으므로 `end_at - start_at`보다 작을 수 있다 |
| avg_cadence | int | nullable | spm (선택). 러닝 전체 평균 — 점별 순간 케이던스(`cadenceSpm`)는 저장하지 않는다 |
| total_elevation_gain | int | nullable | 누적 상승 고도(미터, 선택). **원본 트랙 전체로 한 번 계산한다** — 구간(`running_splits.elevation_change`)의 합과 일치하지 않는다(아래) |
| total_calories | int | nullable | kcal (선택) |
| gps_track_key | varchar | NOT NULL | S3 key — 전체 좌표·시각·고도를 담은 **원본 트랙**. 재계산·분석용이라 **API 응답에는 쓰지 않는다** |
| route_polyline | text | NOT NULL | 다운샘플 경로(encoded polyline, precision 5) — **API가 내려주는 유일한 경로 데이터**. 대시보드(6-2)·기록 목록(7-1)·기록 상세(7-2)·피드 카드가 전부 이 값을 쓴다. **다운샘플 시 구간 경계점을 반드시 보존한다** — `running_splits`의 `route_start_index`·`route_end_index`가 이 배열의 위치를 가리키므로 경계가 틀어지면 구간이 어긋난다 |
| weather_code | int | nullable | WMO 4677 코드(0~99) — 날씨 API 원본값 그대로. 악조건 여부는 저장하지 않고 판정 시 계산한다 |
| temperature | numeric(3,1) | nullable | 섭씨. 영하 포함 |
| start_at / end_at | timestamp | NOT NULL | |
| created_at | timestamp | NOT NULL | 종료(`RUNNING_FINISH`) 시점 일괄 INSERT — 진행 중 PATCH 없음 (write-once) |

> UNIQUE (running_room_id, user_id) — 유저당 방별 1기록. 솔로도 방을 가지므로 부분 인덱스 조건이 필요 없다.
> **개인 단위 진행 상태는 `running_players.status`가 갖는다** — 이 행은 종료 처리의 산물이라 `COMPLETED`와 함께 만들어진다. 방의 `STARTED`→`FINISHED` 전환 규칙은 `feature-spec.md`.
> **경로는 2계층이다** — 화면에 선을 그리는 건 `route_polyline`(DB), 점별 원본은 `gps_track_key`(S3). 기록 목록은 한 번에 수십 건의 경로가 필요해 S3 왕복이 불가능하고 상세도 요구하는 건 선 하나뿐이라, 점별 표시를 켤 때만 S3를 연다.
> **좌표 컬럼은 두지 않는다 — `route_polyline`에서 뽑는다.** 시작점은 폴리라인 앞 몇 글자만 읽으면 되고(O(1)), 종료점은 전체 디코딩이지만 그 응답은 어차피 경로를 통째로 싣는다.
> **날씨(`weather_code`·`temperature`)는 서버가 채운다.** 컬러 획득에 쓰이므로 신고값이면 위조가 가능하다 — 거리·페이스를 서버가 계산하는 것과 같은 원칙이다. INSERT 전에 조회하고 실패하면 null인 채로 넣는다(나중에 UPDATE로 채우면 write-once가 깨진다). 종료 처리가 외부 API에 매이지 않도록 타임아웃을 짧게 둔다.
> **관측값만 저장하고 판정 결과는 저장하지 않는다** — "악조건이었나"를 미리 계산해 넣지 않으므로 컬러 조건표가 바뀌어도 과거 row가 거짓이 되지 않는다(`colors`의 획득 조건을 DB에 두지 않는 것과 같은 원칙). 날씨는 API에도 노출하지 않는다 — 화면에 쓸 곳이 없다. `total_elevation_gain`은 `totalElevationGainMeters`로 응답에 나간다.
> **고도는 기록과 구간이 서로 다른 값을 담는다** — 기록은 **누적 상승**(올라간 것만 합산, 운동 강도), 구간은 **순고도차**(끝 고도 − 시작 고도, 지형 성격). 누적 상승은 GPS 수직 노이즈를 거르려 임계값 이상만 세므로 구간 경계에서 잘린 오르막이 양쪽 모두에서 버려진다 — 그래서 **구간 합 < 기록 값**이 정상이다. 화면에서 구간값을 세로로 더해 총합처럼 보여주지 않는다. 컬러 판정에 쓰는 값은 기록 쪽이다.

### running_splits (구간별)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_split_id | bigint | PK | |
| running_record_id | bigint | FK → running_records, NOT NULL | |
| split_number | int | NOT NULL | 구간 번호(1부터). API `splitNumber` |
| avg_pace | int | NOT NULL | 초/km |
| distance | int | NOT NULL | 구간 거리(미터). 마지막 구간은 1000 미만일 수 있다 |
| duration | int | NOT NULL | 구간 소요 시간(초) |
| avg_cadence | int | nullable | spm (선택). 구간 평균 |
| elevation_change | int | nullable | **순고도차**(미터, 선택) — 끝 고도 − 시작 고도라 **음수가 될 수 있다**(오르막 `+`, 내리막 `−`). 중간 지점이 상쇄되므로 구간 합이 기록 전체의 순고도차와 정확히 맞는다. 기록의 `total_elevation_gain`(누적 상승)과는 다른 값이다 |
| calories | int | nullable | kcal (선택) |
| route_start_index | int | NOT NULL | `running_records.route_polyline`에서 이 구간이 시작하는 점 번호(0부터). 구간 경로를 텍스트로 중복 저장하지 않고 위치만 가리킨다 |
| route_end_index | int | NOT NULL | 끝나는 점 번호(포함). 구간 N의 끝점은 구간 N+1의 시작점과 같아 값이 하나 겹친다 — 한 행만 읽어도 구간을 자를 수 있게 둘 다 저장한다 |
| start_at / end_at | timestamp | NOT NULL | |
| created_at | timestamp | NOT NULL | |

> UNIQUE (running_record_id, split_number) — 기록당 구간 번호 중복 방지.
> **구간 시작점은 `route_start_index`가 가리키는 점이다** — 좌표 컬럼을 따로 두지 않는다.
> **경로를 두 벌 저장하지 않는다** — 구간 경로는 `running_records.route_polyline`의 한 토막이므로 위치만 갖는다. 텍스트로 따로 담으면 같은 좌표가 두 벌이 되고, 기록 폴리라인을 다시 만들 때 두 값이 어긋난다.
> **성립 조건은 다운샘플이 구간 경계점을 보존하는 것이다.** 서버가 Redis 버퍼에서 구간을 나누며 만드는 값이라 경계는 이미 알고 있다 — 구간별로 나눠 다운샘플한 뒤 이으면 자연히 만족한다.
> **구간별 지도 강조**(탭한 구간만)는 클라가 전체 경로를 디코드한 배열을 이 범위로 잘라 그린다. 경로를 그리느라 어차피 디코드한 배열이라 추가 비용이 없다.
> **대가는 `running_records`와의 결합이다** — `route_polyline`을 재생성하면 점 개수가 달라져 그 기록의 모든 구간 인덱스가 무효가 되므로 항상 함께 갱신한다. 둘 다 write-once라 재생성 자체가 예외적 상황이다.

---

## 3. 도메인 C — 소셜 (친구 · 피드 · 댓글)

> **`[MVP 제외]` 표기**: 지금 만들지 않는 테이블. 정의는 그대로 두어 확장 시점에 재작성 없이 쓴다. 마커가 없으면 만드는 것이다.

### friendships

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| requester_id | UUID | PK1, FK → users | 요청을 보낸 쪽 |
| receiver_id | UUID | PK2, FK → users | 요청을 받은 쪽 |
| status | enum | NOT NULL, default PENDING | `PENDING`(수락 대기) / `ACCEPTED`(친구 성립) |
| created_at / updated_at | timestamp | NOT NULL | `updated_at` = 수락 시각. 회원탈퇴 시 CASCADE 삭제 |

> **관계는 대칭이지만 저장은 방향을 갖는다.** 누가 요청했는지 알아야 "받은 요청 목록"을 만들 수 있어 두 컬럼을 구분한다. 성립한 뒤로는 방향에 의미가 없어 친구 목록 조회는 두 컬럼 모두를 본다.
> **역방향 중복은 앱에서 막는다.** 요청 전에 `(A,B)`와 `(B,A)`를 함께 조회해, 역방향에 `PENDING`이 있으면 새 요청을 만들지 않고 **수락으로 처리**한다.
> **거절·삭제는 행을 DELETE한다** — 이력을 보관하지 않는다.
> **친구 수는 집계 테이블 없이 `COUNT`로 구한다** — `status='ACCEPTED'`이면서 두 컬럼 중 하나가 본인인 행을 센다.

### feeds [MVP 제외]

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| feed_id | bigint | PK | |
| running_record_id | bigint | → running_records, nullable | 러닝 기록 템플릿 카드용. **논리 참조**(FK 제약 없음 — 별개 애그리거트, 무결성 앱 레벨). UNIQUE 없음(1기록:N피드 허용) |
| user_id | UUID | → users, NOT NULL | 작성자 |
| content | text | nullable | 캡션 (이미지만 있는 피드 허용) |
| visibility | enum | NOT NULL |  |
| like_count | int | NOT NULL, default 0 | |
| comment_count | int | NOT NULL, default 0 | |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable |  |

### feed_images [MVP 제외]

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| feed_image_id | bigint | PK | |
| feed_id | bigint | FK → feeds, NOT NULL | |
| feed_image_key | varchar | NOT NULL | S3 key |
| mime_type | varchar | nullable | |
| sort_order | int | NOT NULL, default 0 | 표시 순서 |
| created_at | timestamp | NOT NULL | |

### feed_likes [MVP 제외]

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| feed_id | bigint | PK1, FK → feeds | |
| user_id | UUID | PK2, → users | 논리 참조 |
| created_at | timestamp | NOT NULL | |

### comments [MVP 제외]

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| comment_id | bigint | PK | |
| feed_id | bigint | FK → feeds, NOT NULL | |
| parent_comment_id | bigint | FK → comments, nullable | 답글이면 부모 댓글. depth 1단계 제한(답글엔 답글 불가) — 앱 로직 강제, 스키마 미강제 |
| user_id | UUID | → users, NOT NULL | |
| content | text | nullable | 톰스톤(삭제) 시 null. **`feeds.content`와 이름을 맞춘다** — 같은 본문 텍스트다 |
| like_count | int | NOT NULL, default 0 | |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable |  |

### comment_likes [MVP 제외]

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| comment_id | bigint | PK1, FK → comments | |
| user_id | UUID | PK2, → users | |
| created_at | timestamp | NOT NULL | |

---

## 4. 도메인 D — 컬러

> 러닝 지표가 조건을 충족하면 고정 팔레트의 색이 열리는 **잠금 해제 모델**이다. 설계 근거는 `feature-spec.md`의 컬러 시스템 절을 따른다.

### colors (색 마스터)

고정 데이터. 운영이 채워 넣으며 사용자 행동으로 늘어나지 않는다.

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| color_id | bigint | PK | |
| category | enum | NOT NULL | 12범주 ([§6 enum 사전](#6-enum-사전)) |
| shade | int | NOT NULL | 범주 내 순번. 개수는 범주마다 다르다(3~4) |
| name | varchar | NOT NULL | 색 이름("딥 블루") |
| hex_code | varchar(7) | NOT NULL | `#3c62e2` |
| unlock_description | varchar | NOT NULL | 획득 조건 안내 문구("10km 이상 완주") |
| created_at / updated_at | timestamp | NOT NULL | |

> UNIQUE (category, shade) — 범주 내 셰이드 중복 방지.
> 총 색 개수는 고정하지 않는다 — 마스터 행이 늘어도 스키마와 코드가 그대로다.
> **획득 조건은 컬럼으로 두지 않는다.** 조건의 축이 제각각이라 데이터로 표현하기 어렵다 — 판정은 서버 로직에, DB에는 안내 문구(`unlock_description`)만 둔다.

### user_colors (획득 이력)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK1, FK → users | |
| color_id | bigint | PK2, FK → colors | |
| running_record_id | bigint | → running_records, nullable | 획득 계기가 된 러닝. 누적 조건으로 열린 색은 특정 기록에 귀속되지 않아 null |
| created_at | timestamp | NOT NULL | 획득 시각. 회원탈퇴 시 CASCADE 삭제 |

> **복합 PK가 중복 획득을 막는다** — 이미 보유한 색은 다시 지급되지 않는다.
> 컬렉션 진행률은 `user_colors` 보유 수 / `colors` 전체 행 수로 계산한다.

---

## 5. delete_* (스냅샷/이력 테이블)

FK 강제 없는 독립 테이블(원본 삭제/수정된 row를 참조하므로 FK 미설정). 컬럼은 스냅샷 당시 값 그대로, `created_at`(NOT NULL) = 스냅샷 시각.

> **스냅샷은 앱이 남긴다.** `ON DELETE CASCADE`는 DB가 처리하므로 애플리케이션을 거치지 않는다 — 탈퇴로 지워지는 row를 남기려면 탈퇴 유스케이스에서 **명시적으로 INSERT**해야 한다.

### delete_users

회원탈퇴 스냅샷(최소 정보만).
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK, → users | 탈퇴 유저 |
| email | varchar | | |
| alert_consent | boolean | | |
| created_at | timestamp | NOT NULL | 스냅샷 시각 |

### delete_feeds [MVP 제외]

피드 변경 이력(변경 전 내용 스냅샷 — 신고 시 원본 확인용). **수정 시에만 쌓인다** — 피드는 소프트 삭제라 삭제해도 `feeds.content`가 남는다.
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| delete_feed_id | bigint | PK | |
| feed_id | bigint | → feeds | 원본 피드 |
| user_id | UUID | → users | 작성자 |
| content | text | | 스냅샷된 내용 |
| created_at | timestamp | NOT NULL | 스냅샷 시각 |

### delete_comments [MVP 제외]

댓글 변경 이력(변경 전 내용 스냅샷). `delete_feeds`와 **용도가 같고 쌓이는 시점만 다르다** — 댓글은 삭제 시 톰스톤으로 `comments.content`가 null이 되므로 수정뿐 아니라 삭제 시에도 원문을 남긴다.
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| delete_comment_id | bigint | PK | |
| comment_id | bigint | → comments | 원본 댓글 |
| feed_id | bigint | → feeds | |
| parent_comment_id | bigint | → comments | |
| user_id | UUID | → users | |
| content | text | | 스냅샷된 내용 |
| created_at | timestamp | NOT NULL | 스냅샷 시각 |

---

## 6. enum 사전 (컬럼별 값 목록 — DB·API 동일 코드)

| 컬럼 | 값 | 비고 |
|---|---|---|
| feeds.visibility | FRIENDS / PUBLIC / PRIVATE | 피드별 개별 저장 |
| users.profile_visibility | FRIENDS / PUBLIC | 지인 마스킹 — FRIENDS는 `friendships`로 직접 판정 |
| friendships.status | PENDING / ACCEPTED | 수락 대기 / 친구 성립 — 거절은 값이 아니라 row DELETE |
| colors.category | DISTANCE / SPEED / ENDURANCE / CONSISTENCY / CADENCE / INTERVAL / EVEN_PACE / HILLS / RECOVERY / COMPANY / ADVERSITY / MILESTONE | 12범주 — 거리 / 속도 / 지구력 / 꾸준함 / 케이던스 / 인터벌 / 균등페이스 / 언덕 / 회복 / 동행 / 악조건극복 / 이정표 |
| user_onboardings.gender | MALE / FEMALE | |
| user_devices.platform | IOS / ANDROID | |
| running_players.status | INVITED / JOINED / MATCHED_LEFT_PENALTY / MATCHED_LEFT_NO_PENALTY / RUNNING / RUNNING_LEFT_PENALTY / RUNNING_LEFT_NO_PENALTY / COMPLETED | 초대됨 / 참가 / 확정 후 이탈(제재) / 확정 후 이탈(제재 없음) / 러닝 중 / 러닝 중 이탈(제재) / 러닝 중 이탈(제재 없음) / 완주 |
| running_rooms.type | SOLO / MATCH / INVITE | 솔로 러닝 / 랜덤 매칭 / 친구 초대 — 생성 시 확정, 불변 |
| running_rooms.status | MATCHING / MATCHED / STARTED / FINISHED / CANCELLED | 모집 중(마감 전) / 마감 시점 확정 / 시작 / 종료 / 마감 시 2명 미만이거나 참가자 전원이 취소 |
| oauth_users.provider | GOOGLE / KAKAO | |

---

## 7. 인덱스 (조회 성능)

> 복합 PK는 첫 컬럼 조회를 커버한다(`user_colors`·`feed_likes`·`comment_likes`·`running_room_sessions`는 별도 불필요). `running_splits (running_record_id, split_number)` UNIQUE도 마찬가지. `colors`는 마스터라 전체 조회만 하므로 인덱스가 없다.

| 인덱스 대상 | 용도 |
|---|---|
| user_devices.user_id | 푸시 발송 — 유저의 활성 기기 조회 |
| friendships.receiver_id | 받은 요청·친구 목록 (PK는 requester_id만 커버) |
| feeds.user_id | **[MVP 제외]** 프로필 피드 그리드·내 피드 |
| feeds.created_at | **[MVP 제외]** 피드 타임라인 최신순 정렬 |
| feed_images.feed_id | **[MVP 제외]** 피드 이미지 조회 |
| comments.feed_id | **[MVP 제외]** 댓글 목록 |
| comments.parent_comment_id | **[MVP 제외]** 답글 지연 로딩 |
| running_records.user_id | 내 기록·마일리지·러닝 횟수 |
| running_records.running_room_id | 방 결과 조회 |
| running_room_sessions.running_player_id | 참가자의 현재 방 조회 (복합 PK가 `running_room_id` 방향만 커버) |
| running_rooms.(type, status, start_at, target_distance) | 매칭 후보 방 조회 — 같은 슬롯·거리에서 모집 중이고 자리 있는 방(`type='MATCH' AND status='MATCHING'`). 솔로 방·초대방을 인덱스 단계에서 배제하며, 마감 스케줄러도 앞 두 컬럼으로 커버된다 |
| running_players.(user_id, status, deleted_at) | 페널티 판정 — 쿨다운 구간에 제재 대상 이탈(`*_LEFT_PENALTY`)이 있었는지. 대부분 0행이라 조인 없이 끝난다 |
