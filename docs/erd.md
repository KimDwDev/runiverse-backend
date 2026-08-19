# Runiverse ERD (러너버스 데이터 모델)

> 테이블·컬럼명은 PostgreSQL 표준 소문자 `snake_case`. API 표면은 `camelCase`로 매핑(백엔드 담당). 테이블명은 복수형(`users`·`feeds`·`comments` …), FK 컬럼은 참조 테이블의 단수 PK명 그대로 유지(`user_id`·`feed_id`). 자바 엔티티 클래스는 한 행을 표현하므로 단수(`UserJpaEntity`).

---

## 0. 공통 규칙

- **PK 타입**: `users.user_id`만 **UUID**, 그 외 자체 PK는 **bigint**(auto-increment). 연결·좋아요류(`follows`·`feed_likes`·`comment_likes`·`user_badges`·`user_running_contests`·`running_room_sessions`)는 **복합 PK**, 유저당 1 row(`user_onboardings`·`oauth_users`·`user_follow_stats`·`delete_users`)는 **참조 키가 곧 PK**. → API: `userId`만 UUID 문자열, 나머지 Long.
- **FK/참조 네이밍**: 참조 테이블 PK명 그대로(예: `running_records.running_room_id`). 같은 테이블 이중 참조는 역할명(`follows.follower_id`/`followee_id`). `feeds.running_record_id`는 논리 참조(아래 정책).
- **UNIQUE 표기**: 단일 컬럼 = 제약칸, 복합 UNIQUE = 표 아래 블록쿼트(`oauth_users`·`running_records`·`running_splits`).
- **타임스탬프**: 시점을 담는 컬럼은 이름을 `*_at`으로 통일하고 타입은 전부 `timestamp`(시간대 없음, **KST 벽시계로 저장**). 앱이 JVM 기본 타임존을 `APP_TIME_ZONE`으로 고정해 실행 환경과 무관하게 같은 기준을 쓴다(`TimeZoneConfig`). **예외로 달력 날짜**(대회 `event_date`·`registration_start_date`·`registration_end_date`, `user_onboardings.birthday`)만 `*_date` + `date` 타입(시각·시간대 없음, API `YYYY-MM-DD`).
- **감사 컬럼**: `created_at`·`updated_at`은 `NOT NULL`, 앱이 자동 세팅(Hibernate `@CreationTimestamp`/`@UpdateTimestamp`).
- **단위(컬럼에 단위 미표기 — 아래로 통일)**: 거리(`total_distance`·`target_distance`·`distance`) = **미터**, 페이스(`avg_pace`) = **초/km**, 시간(`total_duration`·`duration`) = **초**, 칼로리 = **kcal**, 케이던스(`avg_cadence`) = **spm**, 고도(`total_elevation_gain`·`elevation_change`) = **미터**, 기온(`temperature`) = **섭씨**, 날씨(`weather_code`) = **WMO 4677 코드**.
- **좌표는 컬럼으로 두지 않는다**: 경로는 `route_polyline`(encoded polyline, precision 5), 원본 좌표는 S3의 GPS 트랙(`gps_track_key`)에만 있다. 구간 경로는 polyline을 인덱스로 잘라 쓴다(`running_splits.route_start_index`/`route_end_index`). PostGIS 미사용(위치 기반 기능 도입 시 검토).
- **측정값 접두사**: 사용자가 정한 목표는 `target_*`(`target_distance`), 합계는 `total_*`(`total_distance`·`total_duration`·`total_elevation_gain`·`total_calories`), 평균은 `avg_*`(`avg_pace`·`avg_cadence`). 목표와 실적은 이름으로 갈린다 — `running_players.target_distance`(설정한 목표)와 `running_records.total_distance`(실제 이동 거리)는 다른 값이다. **구간(`running_splits`)은 그 자체가 부분값이라 접두사를 붙이지 않는다**(`distance`·`duration`·`calories`) — 평균값(`avg_pace`·`avg_cadence`)만 예외다.
- **인원 컬럼**: 참가자 수는 `*_player_count`로 통일한다(`max_player_count`·`current_player_count`·`desired_player_count`) — 참조 테이블이 `running_players`라 `member`를 쓰지 않는다.
- **enum**: DB도 API와 **동일한 영문 코드를 그대로 저장**(Java enum `@Enumerated(STRING)`) — 한글 값·변환 매핑 없음. 컬럼별 값 목록은 [§6 enum 사전](#6-enum-사전).
- **소프트 삭제**: `deleted_at`(nullable)이 있는 테이블(`feeds`·`comments`·`running_rooms`)은 소프트 삭제. `running_players.deleted_at`은 **삭제가 아니라 "신청 종료" 시각**이라 의미가 다르다(취소·거절·이탈 이력을 남기려고 row를 지우지 않는다). `delete_*` 테이블은 별도 용도([§5](#5-delete_-스냅샷이력-테이블)).
- **`user_id` FK 정책 (회원탈퇴 연동)**: 탈퇴 시 **CASCADE 삭제**되는 테이블(`user_onboardings`·`oauth_users`·`user_devices`·`follows`·`user_follow_stats`·`user_badges`·`user_running_contests`)은 `user_id` **FK + ON DELETE CASCADE**. **유지**되는 테이블(`feeds`·`comments`·`running_records`·`feed_likes`·`comment_likes`)은 `user_id`를 **논리 참조**(FK 제약 없음 — `users` 하드delete 후 값 유지, 무결성은 앱 레벨). 표기 `→ users`. `running_players`도 **논리 참조**지만 탈퇴 시 **앱이 명시적으로 DELETE**한다 — 이때 `running_room_sessions`은 `running_player_id` FK의 `ON DELETE CASCADE`로 연쇄 삭제된다.
- **`feeds.running_record_id` 참조 정책**: `feeds`↔`running_records`는 별개 애그리거트라 하드 FK 없이 **ID로만 논리 참조**(DDD *Reference by Identity* — `user_id` 논리 참조와 일관). 표기 `→ running_records`. **무결성은 앱 레벨**: 저장 시 `running_records` 존재 검증, 조회 시 유령 참조 방어(기록 카드 미표시).

---

## 1. 도메인 A — 유저 · 인증

### users

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK | |
| email | varchar | UNIQUE, NOT NULL | 로컬·소셜 공통 |
| password_hash | varchar | nullable | 소셜 전용 유저는 null. 원문 미보관 |
| alert_consent | boolean | NOT NULL, default false | 전체 알림 on/off 단일 토글 — 모든 푸시 관장 (설정 13-1/13-2) |
| profile_visibility | enum | NOT NULL, default PUBLIC | 1차 전부 공개 |
| feed_default_visibility | enum | NOT NULL, default PUBLIC | 1차 클라 PUBLIC 고정 |
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
| is_active | boolean | NOT NULL, default true | 재로그인 시 devices API가 true 갱신. 기기 단위 비활성화(로그아웃 시 false)는 deviceId 도입 시(2차) — 1차 로그아웃은 토큰 블랙리스트만 |
| created_at / updated_at | timestamp | NOT NULL | |

---

## 2. 도메인 B — 매칭 · 러닝

> **방은 항상 존재한다**: 솔로 러닝도 1인 방(`type='SOLO'`)을 만든다. 덕분에 `running_records.running_room_id`가 NOT NULL이고, 참가자 조회·기록 저장 경로가 매칭과 솔로에서 동일하다.

### running_rooms

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_room_id | bigint | PK | API `runningRoomId`(Long)가 이 값을 가리킴. **신청·개시 즉시 1인 방으로 생성** — 매칭은 `MATCHING`, 솔로는 `STARTED`로 시작 |
| type | enum | NOT NULL | `SOLO` / `MATCH` / `INVITE`. 생성 시 정해지고 바뀌지 않는다 — 매칭 후보 스캔·대기 인원 집계가 `type='MATCH'`만 보므로 솔로 방과 초대방이 섞이지 않는다 |
| status | enum | NOT NULL, default MATCHING | 진행 단계 — 값 목록은 [§6](#6-enum-사전) |
| start_at | timestamp | NOT NULL | 예약 시작 시각 |
| close_at | timestamp | nullable | 모집 마감 시각(`start_at - 설정값`). **생성 시 고정** — 설정을 바꿔도 진행 중인 방의 마감이 움직이지 않는다. 스케줄러가 `type='MATCH' AND status='MATCHING' AND close_at <= now()`로 찾으므로 계산식이 아니라 컬럼이어야 인덱스를 탄다. 모집 단계가 없는 솔로는 null |
| target_distance | int | nullable | 방의 목표 거리(미터). 매칭 조건이라 **정해진 뒤에는 바뀌지 않는다**. 참가자에게서 유추하지 않고 방이 직접 갖는다 — 후보 방 조회가 단일 테이블에서 끝난다 |
| avg_pace | int | nullable | 참가자 평균 페이스(초/km). 참가·이탈마다 갱신. 배정 시 페이스가 가까운 방을 고르는 데 쓰고, API 응답의 팀 평균 페이스로도 나간다 |
| max_player_count | int | NOT NULL | 자리 수 — 매칭 `4`, 솔로 `1`. **생성 시 정해지고 갱신하지 않는다** |
| current_player_count | int | NOT NULL, default 1 | 현재 인원. 생성 시 `1`, 참가·이탈마다 갱신. `current_player_count < max_player_count`면 들어갈 수 있다. 러닝 중에는 변하지 않으므로 `STARTED` 이후 값이 곧 출발 인원이다 |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable | **[MVP 제외]** 관리자 부정 방 숨김용 |

> **후보 방 배정**: 매칭 신청 시 `type='MATCH' AND status='MATCHING' AND current_player_count < max_player_count`인 방 중 `target_distance`·`start_at`이 맞고 `avg_pace`가 가까운 방을 고른다. 없으면 새 방을 만든다(1인 방).
> **마감 판정**: `close_at` 도달 시 스케줄러가 `current_player_count >= 2`면 `MATCHED`, `1`이면 `CANCELLED`. `max_player_count` 도달 여부와 무관하다.

### running_players

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_player_id | bigint | PK | 매칭 요청 = 이 row |
| user_id | UUID | → users, NOT NULL | 논리 참조(FK 제약 없음). 탈퇴 시 앱이 명시적으로 삭제한다 |
| status | enum | NOT NULL, default JOINED | 참가·진행 상태 — 값 목록은 [§6](#6-enum-사전) |
| avg_pace | int | NOT NULL | 매칭 희망 페이스(초/km, 서버가 유저 평균에서 세팅) |
| target_distance | int | NOT NULL | 목표 거리(미터, API `targetDistanceMeters`). **목표는 `target_*`, 실적은 `total_*`** — `running_records.total_distance`(실제 이동 거리)와 이름으로 갈린다 |
| start_at | timestamp | NOT NULL | 희망 시작 시각 |
| desired_player_count | int | nullable | **[MVP 제외]** 유저 희망 매칭 인원 — 서버가 2~4명으로 자동 편성 |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable | **신청이 끝난 시각** — 대기 취소·초대 거절·이탈 공통. 한 번 찍히면 바뀌지 않는다. 정상 완주(`COMPLETED`)에는 찍지 않는다 |

> `running_players`는 `running_room_id` FK 없음 — 매칭 조건을 담은 "요청" 엔티티, 방과는 연결 테이블로 약결합.
> 매칭 후보 스캔·중복 신청 검사는 `deleted_at IS NULL`을 항상 함께 본다.

### running_room_sessions (연결 테이블)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_room_id | bigint | PK1, FK → running_rooms | 배정된 방 |
| running_player_id | bigint | PK2, FK → running_players, ON DELETE CASCADE | 플레이어 삭제(탈퇴 시 앱이 삭제) 시 링크도 연쇄 삭제 |
| leave_count | int | NOT NULL, default 0 | 러닝 중 연결이 끊긴 횟수 — 페널티 판정 근거 |
| is_connected | boolean | NOT NULL, default false | 현재 WS 연결 여부 |
| created_at | timestamp | NOT NULL | |

> **이름 혼동 주의**: 이 테이블은 API 미노출, 서버 내부 연결용이다. API의 러닝 세션 식별자는 `running_rooms.running_room_id`를 가리킨다.
> **"한 플레이어 = 최대 한 방"은 DB가 강제하지 않는다** — 복합 PK의 선두가 `running_room_id`라 `running_player_id` 단독 유일성은 보장되지 않는다. 중복 링크 차단은 앱 레벨(신청 시 `deleted_at IS NULL`인 기존 player 존재 검사)이다.
> **row 트리거**: 생성 = 방 배정 시(솔로는 방 생성과 동시) / 삭제 = 대기 취소·초대 거절 시(`running_players`는 `deleted_at`만 찍고 유지) / 확정 후 이탈 = 링크 유지 + `running_players.status`를 `*_LEFT_*`로 전환(어느 방에서 나갔는지 = 페널티 근거) / 방 자동 취소 = 전원 유지(방 status만 `CANCELLED`).

### running_records

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_record_id | bigint | PK | |
| running_room_id | bigint | FK → running_rooms, NOT NULL | 솔로 러닝도 방을 만드므로 항상 값이 있다 |
| user_id | UUID | → users, NOT NULL | 논리 참조 |
| avg_pace | int | NOT NULL | 초/km |
| total_distance | int | NOT NULL | 미터 |
| total_duration | int | NOT NULL | 초 |
| avg_cadence | int | nullable | spm (선택) |
| total_elevation_gain | int | nullable | **누적 상승 고도**(미터, 선택). 오르막 구간의 상승분만 더한 값이라 항상 0 이상 — `running_splits.elevation_change`(순변화)의 합과 **일치하지 않는다** |
| total_calories | int | NOT NULL | kcal — 서버가 러닝 종료 시점에 계산한다 |
| gps_track_key | varchar | NOT NULL | S3 key — 전체 좌표·시각·고도를 담은 **원본 트랙**. **API 응답에는 쓰지 않는다** — 재계산·분석용(고도 소급 계산 등). 매칭·솔로 모두 서버가 업로드(Redis 버퍼→S3) |
| route_polyline | text | NOT NULL | 다운샘플 경로(encoded polyline, precision 5) — **API가 내려주는 유일한 경로 데이터**. 대시보드·기록 목록·기록 상세·피드 카드가 전부 이 값을 쓴다. 조회 한 번에 딸려 나와 S3 왕복이 없다. 매칭·솔로 모두 서버가 Redis 버퍼로 생성. `running_splits`의 구간 경로도 이 값을 인덱스로 잘라 쓴다 |
| weather_code | int | NOT NULL | WMO 4677 코드(0~99) — 날씨 API 원본값 그대로. 악조건 여부는 저장하지 않고 판정 시 계산한다. **클라이언트가 `RUNNING_FINISH`에 실어 보낸다**(아래 참조) |
| temperature | numeric(3,1) | NOT NULL | 섭씨. 영하 포함 |
| start_at / end_at | timestamp | NOT NULL | |
| created_at | timestamp | NOT NULL | 종료(`RUNNING_FINISH`) 시점 일괄 INSERT — 진행 중 PATCH 없음 (write-once) |

> UNIQUE (running_room_id, user_id) — 유저당 방별 1기록. 솔로도 방이 있으므로 부분 인덱스가 아니다.
> 좌표 컬럼(`start_lat`/`end_lat` 등)은 두지 않는다 — 시작·종료 지점은 `route_polyline`의 양 끝점이고, 정밀 좌표가 필요하면 `gps_track_key`의 원본 트랙을 읽는다.
> **날씨는 클라이언트가 보낸다**: `weather_code`·`temperature`는 종료 신호(`RUNNING_FINISH`)에 담겨 온다. 서버가 종료 시점에 외부 날씨 API를 호출하는 방식은 쓰지 않는다 — 그 API가 실패하면 한 시간짜리 러닝 기록 저장이 통째로 실패하기 때문이다. 클라이언트는 홈 화면에서 이미 같은 API를 쓰고 있어 추가 비용이 없다.

### running_splits (구간별)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_split_id | bigint | PK | |
| running_record_id | bigint | FK → running_records, NOT NULL | |
| split_number | int | NOT NULL | 구간 번호(1부터). API `splitNumber` |
| avg_pace | int | NOT NULL | 초/km |
| distance | int | NOT NULL | 구간 거리(미터). 마지막 구간은 1000 미만일 수 있다 |
| duration | int | NOT NULL | 구간 소요 시간(초) |
| avg_cadence | int | nullable | spm (선택) |
| calories | int | NOT NULL | kcal — 구간 값이라 `total_` 접두사가 붙지 않는다 |
| elevation_change | int | nullable | **고도 순변화**(미터, 선택) — 구간 시작점과 끝점의 고도 차. 내리막 구간은 **음수**다. 누적 상승만 세는 `running_records.total_elevation_gain`과 기준이 달라 구간을 다 더해도 전체와 일치하지 않는다 |
| route_start_index / route_end_index | int | NOT NULL | 구간 경로 — `running_records.route_polyline`을 디코딩한 좌표 배열의 인덱스 범위. 구간 경로를 따로 저장하지 않고 잘라 쓴다 |
| start_at / end_at | timestamp | NOT NULL | |
| created_at | timestamp | NOT NULL | |

> UNIQUE (running_record_id, split_number) — 기록당 구간 번호 중복 방지.
> **인덱스 기준**: `route_start_index`/`route_end_index`는 **`route_polyline`을 디코딩한 다운샘플 좌표 배열**의 인덱스다(S3 원본 트랙 기준이 아니다 — API가 내려주는 게 polyline뿐이라 클라이언트가 원본 인덱스를 쓸 수 없다).
> **경계 규칙**: `route_end_index`는 **inclusive이고 다음 구간의 `route_start_index`와 겹친다**(구간 n의 끝점 = 구간 n+1의 시작점). 겹치지 않으면 구간을 이어 그릴 때 경계마다 선이 끊긴다. 따라서 구간 포인트 수의 합은 전체 포인트 수보다 (구간 수 - 1)만큼 많다.
> **재생성 주의**: 인덱스는 `route_polyline`의 다운샘플 결과에 종속된다. 경로를 다시 만들면 저장된 인덱스가 조용히 다른 좌표를 가리키므로, **`route_polyline`과 인덱스는 항상 한 트랜잭션에서 같이 갱신**한다.

---

## 3. 도메인 C — 소셜 (팔로우 · 피드 · 댓글 · 뱃지)

### follows

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| follower_id | UUID | PK1, FK → users | 팔로우 거는 쪽 |
| followee_id | UUID | PK2, FK → users | 팔로우 받는 쪽 |
| created_at | timestamp | NOT NULL | 맞팔(양방향 존재) = "친구/지인". 회원탈퇴 시 CASCADE 삭제 |

### user_follow_stats

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK, FK → users | |
| follower_count | int | NOT NULL, default 0 | |
| following_count | int | NOT NULL, default 0 | follow 변동 시 즉시 재계산 |
| created_at / updated_at | timestamp | NOT NULL | |

> 동기화: 팔로우/언팔로우/탈퇴를 같은 트랜잭션에서 ±1 처리. 드리프트 대비 주기적 재계산 배치 권장.

### feeds

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

### feed_images

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| feed_image_id | bigint | PK | |
| feed_id | bigint | FK → feeds, NOT NULL | |
| feed_image_key | varchar | NOT NULL | S3 key |
| mime_type | varchar | nullable | |
| sort_order | int | NOT NULL, default 0 | 표시 순서 |
| created_at | timestamp | NOT NULL | |

### feed_likes

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| feed_id | bigint | PK1, FK → feeds | |
| user_id | UUID | PK2, → users | 논리 참조 |
| created_at | timestamp | NOT NULL | |

### comments

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| comment_id | bigint | PK | |
| feed_id | bigint | FK → feeds, NOT NULL | |
| parent_comment_id | bigint | FK → comments, nullable | 답글이면 부모 댓글. depth 1단계 제한(답글엔 답글 불가) — 앱 로직 강제, 스키마 미강제 |
| user_id | UUID | → users, NOT NULL | |
| comment | text | nullable | 톰스톤(삭제) 시 null |
| like_count | int | NOT NULL, default 0 | |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable |  |

### comment_likes

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| comment_id | bigint | PK1, FK → comments | |
| user_id | UUID | PK2, → users | |
| created_at | timestamp | NOT NULL | |

### badges

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| badge_id | bigint | PK | |
| name | varchar | UNIQUE, NOT NULL | |
| description | varchar | nullable | |
| image_url | varchar | NOT NULL | |
| created_at | timestamp | NOT NULL | |

### user_badges

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK1, FK → users | |
| badge_id | bigint | PK2, FK → badges | |
| created_at | timestamp | NOT NULL | 유저 뱃지 보유(다대다). 프로필 화면에서만 노출 |

---

## 4. 도메인 D — 대회

### running_contests

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_contest_id | bigint | PK | API `contestId` |
| name | varchar | NOT NULL | |
| region / venue | varchar | nullable | |
| event_date | date | NOT NULL | |
| distances | numeric[] | NOT NULL | km 단위 배열(예: {5,10,21.0975,42.195}) — API는 미터로 변환 노출 |
| thumbnail_image_url | varchar | nullable | 외부 URL |
| registration_start_date / registration_end_date | date | nullable | 접수 시작/마감일 — 달력 날짜(시각 없음), API는 `YYYY-MM-DD` |
| detail_url | varchar | nullable | 외부 상세 링크 |
| created_at / updated_at | timestamp | NOT NULL | |

### user_running_contests (북마크)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK1, FK → users | |
| running_contest_id | bigint | PK2, FK → running_contests | |
| created_at | timestamp | NOT NULL | 관심 대회 북마크(참가 신청 아님, 단순 연결) |

---

## 5. delete_* (스냅샷/이력 테이블)

FK 강제 없는 독립 테이블(원본 삭제/수정된 row를 참조하므로 FK 미설정). 컬럼은 스냅샷 당시 값 그대로, `created_at`(NOT NULL) = 스냅샷 시각.

### delete_users

회원탈퇴 스냅샷(최소 정보만).
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK, → users | 탈퇴 유저 |
| email | varchar | | |
| alert_consent | boolean | | |
| created_at | timestamp | NOT NULL | 스냅샷 시각 |

### delete_feeds

피드 수정 이력(수정 시마다 이전 내용 스냅샷 — 신고 시 원본 확인용).
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| delete_feed_id | bigint | PK | |
| feed_id | bigint | → feeds | 원본 피드 |
| user_id | UUID | → users | 작성자 |
| content | text | | 스냅샷된 내용 |
| created_at | timestamp | NOT NULL | 스냅샷 시각 |

### delete_comments

댓글 삭제/수정 이력.
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| delete_comment_id | bigint | PK | |
| comment_id | bigint | → comments | 원본 댓글 |
| feed_id | bigint | → feeds | |
| parent_comment_id | bigint | → comments | |
| user_id | UUID | → users | |
| comment | text | | 스냅샷된 내용 |
| created_at | timestamp | NOT NULL | 스냅샷 시각 |

### delete_badges

뱃지 삭제 이력.
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| delete_badge_id | bigint | PK | |
| badge_id | bigint | → badges | 원본 뱃지 |
| name | varchar | | |
| description | varchar | | |
| image_url | varchar | | |
| created_at | timestamp | NOT NULL | 스냅샷 시각 |

---

## 6. enum 사전 (컬럼별 값 목록 — DB·API 동일 코드)

| 컬럼 | 값 | 비고 |
|---|---|---|
| feeds.visibility | FOLLOWERS / PUBLIC / PRIVATE | 피드별 개별 저장 |
| users.profile_visibility | FRIENDS / PUBLIC | **[2차]** 지인 마스킹 |
| users.feed_default_visibility | FOLLOWERS / PUBLIC / PRIVATE | **[2차]** 피드 기본 공개 범위 |
| user_onboardings.gender | MALE / FEMALE | |
| user_devices.platform | IOS / ANDROID | |
| running_rooms.type | SOLO / MATCH / INVITE | 솔로 러닝 / 랜덤 매칭 / 친구 초대. 생성 시 고정 |
| running_rooms.status | MATCHING / MATCHED / STARTED / FINISHED / CANCELLED | 모집 중 / 마감 확정 / 시작 / 종료 / 취소. `SOLO`는 `STARTED`로 생성돼 `MATCHING`·`MATCHED`를 거치지 않는다 |
| running_players.status | INVITED / JOINED / MATCHED_LEFT_PENALTY / MATCHED_LEFT_NO_PENALTY / RUNNING / RUNNING_LEFT_PENALTY / RUNNING_LEFT_NO_PENALTY / COMPLETED | 아래 표 참조 |

**`running_players.status` 상세**

| 값 | 의미 | `deleted_at` |
|---|---|---|
| INVITED | 초대받고 수락 전 (`INVITE` 방 전용) | 거절 시 찍는다 |
| JOINED | 방에 참가 중 — 모집 대기·마감 확정 공통 (default) | 대기 취소 시 찍는다 |
| MATCHED_LEFT_PENALTY | 마감 확정 후 시작 전 이탈 — 페널티 대상 | 찍는다 |
| MATCHED_LEFT_NO_PENALTY | 마감 확정 후 이탈이지만 페널티 면제(방 취소 등 본인 귀책 아님) | 찍는다 |
| RUNNING | 러닝 진행 중 | null |
| RUNNING_LEFT_PENALTY | 러닝 중 이탈 — 페널티 대상 | 찍는다 |
| RUNNING_LEFT_NO_PENALTY | 러닝 중 이탈이지만 페널티 면제 | 찍는다 |
| COMPLETED | 기록 제출까지 완료 (정상 종료) | null |

> **페널티 판정은 이탈 시점에 끝난다** — 서버가 `*_PENALTY` / `*_NO_PENALTY` 중 하나로 고정 저장하므로 별도 페널티 테이블 없이 이 컬럼이 근거가 된다. 판정 기준(`leave_count` 임계값 등)은 운영 정책.
> **취소·거절에는 별도 status 값이 없다** — `JOINED`/`INVITED` 상태 그대로 `deleted_at`만 찍고, `running_room_sessions` 링크는 삭제한다. 이탈과 달리 방 이력을 남길 필요가 없기 때문이다.
| oauth_users.provider | GOOGLE / KAKAO | |
| (API 전용) emojiType | HI / CHEER / FIGHTING / FIRE / LAUGH | WS 이모티콘 — DB 컬럼 없음(비영속). 인사/응원/파이팅/준비 완료/웃음, 추가는 하위 호환 |

---

## 7. 인덱스 (조회 성능)

> 복합 PK는 첫 컬럼 조회를 커버(`feed_likes`·`comment_likes`·`user_badges`·`user_running_contests`는 별도 불필요). `running_room_sessions (running_room_id, running_player_id)` PK가 방 참가자 조회를, `running_splits (running_record_id, split_number)` UNIQUE가 기록별 구간 조회를, `running_records (running_room_id, user_id)` UNIQUE가 방 결과 조회를 각각 커버한다.

| 인덱스 대상 | 용도 |
|---|---|
| user_devices.user_id | 푸시 발송 — 유저의 활성 기기 조회 |
| follows.followee_id | 팔로워 목록 (PK는 follower_id만 커버) |
| feeds.user_id | 프로필 피드 그리드·내 피드 |
| feeds.created_at | 피드 타임라인 최신순 정렬 |
| feed_images.feed_id | 피드 이미지 조회 |
| comments.feed_id | 댓글 목록 |
| comments.parent_comment_id | 답글 지연 로딩 |
| running_rooms (type, status, close_at) | 스케줄러 모집 마감 판정 — `type='MATCH' AND status='MATCHING' AND close_at <= now()` |
| running_rooms (type, status, start_at, target_distance, avg_pace) | 매칭 후보 방 스캔 — 앞 4개는 등가 조건, `avg_pace`는 범위(±30초/km) 조건이라 마지막에 둔다 |
| running_players.user_id | 내 신청 조회·중복 신청 검사·탈퇴 시 삭제 (논리 참조라 FK 인덱스가 없음) |
| running_room_sessions.running_player_id | 플레이어 → 배정된 방 역방향 조회 (복합 PK 선두가 room_id라 미커버) |
| running_records.user_id | 내 기록·마일리지·누적고도·잔디 |
| running_contests.region, event_date | 대회 검색·필터 |
