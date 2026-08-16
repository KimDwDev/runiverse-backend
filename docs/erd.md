# Runiverse ERD (러너버스 데이터 모델)

> 테이블·컬럼명은 PostgreSQL 표준 소문자 `snake_case`. API 표면은 `camelCase`로 매핑(백엔드 담당). 테이블명은 복수형(`users`·`feeds`·`comments` …), FK 컬럼은 참조 테이블의 단수 PK명 그대로 유지(`user_id`·`feed_id`). 자바 엔티티 클래스는 한 행을 표현하므로 단수(`UserJpaEntity`).

---

## 0. 공통 규칙

- **PK 타입**: `users.user_id`만 **UUID**, 그 외 자체 PK는 **bigint**(auto-increment). 연결·좋아요류(`follows`·`feed_likes`·`comment_likes`·`user_running_contests`)는 **복합 PK**, 유저당 1 row(`user_onboardings`·`oauth_users`·`user_follow_stats`·`delete_users`)·`running_room_sessions`은 **참조 키가 곧 PK**. → API: `userId`만 UUID 문자열, 나머지 Long.
- **FK/참조 네이밍**: 참조 테이블 PK명 그대로(예: `running_records.running_room_id`). 같은 테이블 이중 참조는 역할명(`follows.follower_id`/`followee_id`). `feeds.running_record_id`는 논리 참조(아래 정책).
- **UNIQUE 표기**: 단일 컬럼 = 제약칸, 복합 UNIQUE = 표 아래 블록쿼트(`oauth_users`·`running_records`·`running_splits`).
- **타임스탬프**: `*_at`은 전부 `timestamp`(시간대 없음, **KST 벽시계로 저장**). 앱이 JVM 기본 타임존을 `APP_TIME_ZONE`으로 고정해 실행 환경과 무관하게 같은 기준을 쓴다(`TimeZoneConfig`). `*_date`도 시점이면 `timestamp`. **예외로 달력 날짜**(대회 `event_date`·`registration_start_date`·`registration_end_date`, `user_onboardings.birthday`)는 `date`(시각·시간대 없음, API `YYYY-MM-DD`).
- **감사 컬럼**: `created_at`·`updated_at`은 `NOT NULL`, 앱이 자동 세팅(Hibernate `@CreationTimestamp`/`@UpdateTimestamp`).
- **단위(컬럼에 단위 미표기 — 아래로 통일)**: 거리 = **미터**, 페이스(`avg_pace`) = **초/km**, 시간(`total_time`·`session_time`) = **초**, 칼로리 = **kcal**, 케이던스(`cadence`) = **spm**, 누적 상승 고도(`elevation_gain`) = **미터**. 좌표(`*_lat`/`*_lng`) = **`double precision`**(degree). PostGIS 미사용(위치 기반 기능 도입 시 검토).
- **enum**: DB도 API와 **동일한 영문 코드를 그대로 저장**(Java enum `@Enumerated(STRING)`) — 한글 값·변환 매핑 없음. 컬럼별 값 목록은 [§6 enum 사전](#6-enum-사전).
- **소프트 삭제**: `deleted_at`(nullable)이 있는 테이블(`feeds`·`comments`·`running_rooms`)은 소프트 삭제. `delete_*` 테이블은 별도 용도([§5](#5-delete_-스냅샷이력-테이블)).
- **`user_id` FK 정책 (회원탈퇴 연동)**: 탈퇴 시 **CASCADE 삭제**되는 테이블(`user_onboardings`·`oauth_users`·`user_devices`·`follows`·`user_follow_stats`·`user_running_contests`·`running_players`)은 `user_id` **FK + ON DELETE CASCADE**. `running_players` 삭제는 연결 테이블 `running_room_sessions`으로 연쇄(아래 참조). **유지**되는 테이블(`feeds`·`comments`·`running_records`·`feed_likes`·`comment_likes`)은 `user_id`를 **논리 참조**(FK 제약 없음 — `users` 하드delete 후 값 유지, 무결성은 앱 레벨). 표기 `→ users`
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
| profile_visibility | enum | NOT NULL, default PUBLIC | 지인 마스킹 on/off |
| feed_default_visibility | enum | NOT NULL, default PUBLIC | **[MVP 제외]** 피드 기본 공개 범위 |
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
| running_room_id | bigint | PK | API `runningSessionId`(Long)가 이 값을 가리킴. **2명째가 매칭되는 순간 생성** — 신청 시점엔 방이 없다 |
| start_date | timestamp | NOT NULL | 예약 시작 시각 |
| total_member | int | NOT NULL | 모집 인원(서버 자동 편성 2~4) |
| running_member | int | nullable | 실제 러닝 인원(러닝 시작 후 확정) |
| status | enum | NOT NULL, default MATCHING |  |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable | **[MVP 제외]** 관리자 부정 방 숨김용 |

### running_players

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_player_id | bigint | PK | 매칭 요청 = 이 row |
| user_id | UUID | FK → users, NOT NULL | |
| status | enum | NOT NULL, default CONFIRMED | **참가 의사** 축(매칭 진행 단계 아님) — 신청 즉시 CONFIRMED가 맞다 |
| avg_pace | int | NOT NULL | 매칭 희망 페이스(초/km, 서버가 유저 평균에서 세팅) |
| total_distance | int | NOT NULL | 목표 거리(미터, API `targetDistanceMeters`) |
| start_date | timestamp | NOT NULL | 희망 시작 시각 |
| desired_member_count | int | nullable | **[MVP 제외]** 유저 희망 매칭 인원 — 서버가 2~4명으로 자동 편성 |
| created_at / updated_at | timestamp | NOT NULL | |

> `running_players`는 `running_room_id` FK 없음 — 매칭 조건을 담은 "요청" 엔티티, 방과는 연결 테이블로 약결합.
> **`status`는 참가 의사만 표현한다.** 매칭이 어디까지 갔는지는 방 배정 여부와 `running_rooms.status`가 갖는다 — 진행 단계 값(`WAITING`·`FAILED` 등)을 이 컬럼에 추가하지 말 것(같은 사실 이중 저장 → 드리프트).

### running_room_sessions (연결 테이블)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_player_id | bigint | PK, FK → running_players, ON DELETE CASCADE | 한 플레이어 = 최대 한 방 (PK가 유일성 보장 — 여러 방 동시 링크 차단). 플레이어 삭제(탈퇴 CASCADE 포함) 시 링크도 연쇄 삭제 |
| running_room_id | bigint | FK → running_rooms, NOT NULL | 배정된 방 |
| created_at | timestamp | NOT NULL | |

> **이름 혼동 주의**: API "세션"(`runningSessionId`) = `running_rooms.running_room_id`. 이 테이블은 API 미노출, 서버 내부 연결용.
> **row 트리거**: 링크 생성 = 방 배정 시 / 삭제 = 대기 취소·초대 거절 시(`running_players`도 DELETE) / 확정 후 이탈 = 링크 유지 + `running_players.status=LEFT`(어느 방에서 나갔는지 = 페널티 근거) / 방 자동 취소 = 전원 유지(방 status만 CANCELLED).

### running_records

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_record_id | bigint | PK | |
| running_room_id | bigint | FK → running_rooms, nullable | 솔로 러닝은 방 없음(null), 매칭 러닝만 방 참조 |
| user_id | UUID | → users, NOT NULL | |
| avg_pace | int | NOT NULL | 초/km |
| total_distance | int | NOT NULL | 미터 |
| total_time | int | NOT NULL | 초 |
| cadence | int | nullable | spm (선택) |
| elevation_gain | int | nullable | 누적 상승 고도(미터, 선택) |
| calories | int | nullable | kcal (선택) |
| gps_track_key | varchar | NOT NULL | S3 key. 매칭=서버 업로드(Redis 버퍼→S3), 솔로=클라 업로드(presigned URL). 2차 실내러닝 시 nullable |
| route_polyline | text | NOT NULL | 다운샘플 경로(encoded polyline) — 피드 카드 지도 미리보기용. 매칭=서버 생성(Redis 버퍼), 솔로=클라 제출. 2차 실내러닝 시 nullable |
| start_date / end_date | timestamp | NOT NULL | |
| start_lat / start_lng / end_lat / end_lng | double precision | NOT NULL | |
| created_at | timestamp | NOT NULL | 종료(`RUNNING_FINISH`) 시점 일괄 INSERT — 진행 중 PATCH 없음 (write-once) |

> UNIQUE (running_room_id, user_id) WHERE running_room_id IS NOT NULL — 매칭 러닝은 유저당 방별 1기록 (솔로는 room null이라 제외).

### running_splits (구간별)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_split_id | bigint | PK | |
| running_record_id | bigint | FK → running_records, NOT NULL | |
| sequence | int | NOT NULL | 구간 순번 |
| avg_pace | int | NOT NULL | 초/km |
| session_distance | int | NOT NULL | 구간 거리(미터) |
| session_time | int | NOT NULL | 구간 시간(초) |
| cadence | int | nullable | spm (선택) |
| elevation_gain | int | nullable | 누적 상승 고도(미터, 선택) |
| calories | int | nullable | kcal (선택) |
| session_start_date / session_end_date | timestamp | NOT NULL | |
| session_lat / session_lng | double precision | NOT NULL | 구간 시작점 |
| created_at | timestamp | NOT NULL | |

> UNIQUE (running_record_id, sequence) — 기록당 구간 순번 중복 방지.

---

## 3. 도메인 C — 소셜 (팔로우 · 피드 · 댓글)

> **`[MVP 제외]` 표기**: 지금 만들지 않는 테이블. 정의는 그대로 두어 확장 시점에 재작성 없이 쓴다. 마커가 없으면 만드는 것이다.

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
| comment | text | nullable | 톰스톤(삭제) 시 null |
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

## 4. 도메인 D — 대회 [MVP 제외]

### running_contests [MVP 제외]

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

### user_running_contests (북마크) [MVP 제외]

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

### delete_feeds [MVP 제외]

피드 수정 이력(수정 시마다 이전 내용 스냅샷 — 신고 시 원본 확인용).
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| delete_feed_id | bigint | PK | |
| feed_id | bigint | → feeds | 원본 피드 |
| user_id | UUID | → users | 작성자 |
| content | text | | 스냅샷된 내용 |
| created_at | timestamp | NOT NULL | 스냅샷 시각 |

### delete_comments [MVP 제외]

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

---

## 6. enum 사전 (컬럼별 값 목록 — DB·API 동일 코드)

| 컬럼 | 값 | 비고 |
|---|---|---|
| feeds.visibility | FOLLOWERS / PUBLIC / PRIVATE | 피드별 개별 저장 |
| users.profile_visibility | FRIENDS / PUBLIC | 지인 마스킹 |
| users.feed_default_visibility | FOLLOWERS / PUBLIC / PRIVATE | **[MVP 제외]** 피드 기본 공개 범위 |
| user_onboardings.gender | MALE / FEMALE | |
| user_devices.platform | IOS / ANDROID | |
| running_players.status | INVITED / CONFIRMED / LEFT | 초대됨 / 참가중 / 이탈 — **참가 의사 축**(매칭 단계 아님) |
| running_rooms.status | MATCHING / MATCHED / STARTED / FINISHED / CANCELLED | 2명 이상 모였으나 마감 전 / 마감 시점 확정 / 시작 / 종료 / 확정 후 이탈로 2명 미만 |
| oauth_users.provider | GOOGLE / KAKAO | |

---

## 7. 인덱스 (조회 성능)

> 복합 PK는 첫 컬럼 조회를 커버(`feed_likes`·`comment_likes`·`user_running_contests`는 별도 불필요). `running_splits (running_record_id, sequence)` UNIQUE도 `running_record_id` 조회 커버.

| 인덱스 대상 | 용도 |
|---|---|
| user_devices.user_id | 푸시 발송 — 유저의 활성 기기 조회 |
| follows.followee_id | 팔로워 목록 (PK는 follower_id만 커버) |
| feeds.user_id | **[MVP 제외]** 프로필 피드 그리드·내 피드 |
| feeds.created_at | **[MVP 제외]** 피드 타임라인 최신순 정렬 |
| feed_images.feed_id | **[MVP 제외]** 피드 이미지 조회 |
| comments.feed_id | **[MVP 제외]** 댓글 목록 |
| comments.parent_comment_id | **[MVP 제외]** 답글 지연 로딩 |
| running_records.user_id | 내 기록·마일리지·러닝 횟수 |
| running_records.running_room_id | 방 결과 조회 |
| running_room_sessions.running_room_id | 방 참가자 조회 |
| running_contests.region, event_date | **[MVP 제외]** 대회 검색·필터 |
