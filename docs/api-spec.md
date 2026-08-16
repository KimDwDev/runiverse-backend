# Runiverse API 명세서

> 구성: 엔드포인트 목록(색인) → 상세 명세 0~13. ERD 기준: `erd.md`

---

## 엔드포인트 목록 (색인)

같은 API가 여러 화면에서 쓰이면 처음 등장하는 화면에 한 번만 적고 "사용 화면"으로 표시.

### 1. 인증·온보딩 (초기 페이지 / 회원가입 페이지 / 온보딩 화면)

| # | Method | Path | 설명 |
|---|--------|------|------|
| 1 | POST | `/api/v1/auth/email/verifications` | 이메일 인증번호(6자리) 발급 — 회원가입 1단계 |
| 2 | POST | `/api/v1/auth/email/verifications/confirm` | 이메일 인증번호 확인 → `verificationTicket` 발급 — 회원가입 2단계 |
| 3 | POST | `/api/v1/auth/signup` | 로컬 회원가입 (인증 티켓/비밀번호) — 가입 즉시 자동 로그인 |
| 4 | POST | `/api/v1/auth/login` | 로컬 로그인 |
| 5 | POST | `/api/v1/auth/oauth/google` | 구글 로그인 — 인가 코드+PKCE 서버 교환 → 토큰 발급 |
| 6 | POST | `/api/v1/auth/oauth/kakao` | 카카오 로그인 — 인가 코드+PKCE 서버 교환 → 토큰 발급 |
| 7 | POST | `/api/v1/auth/refresh` | 토큰 재발급 (rotation — accessToken·refreshToken 모두 교체) |
| 8 | POST | `/api/v1/auth/logout` | 로그아웃 — access 토큰 서버 차단(블랙리스트) — 사용 화면: 설정 페이지 |
| 9 | POST | `/api/v1/users/onboarding` | 온보딩 입력 (닉네임 포함, 1회성) |

### 2. 공통 — 디바이스/푸시

| # | Method | Path | 설명 |
|---|--------|------|------|
| 10 | POST | `/api/v1/devices` | 디바이스(푸시 토큰) 등록/갱신, `isActive=true` 전환 — 사용 화면: 로그인 직후 전역 |

### 3. 홈 화면

- 날씨: 서버 API 없음 — 클라이언트가 직접 호출 (상세 3번)
- 매칭 시작·설정·대기: REST 없음 — 전부 WebSocket (아래 5번)

### 4. 매칭완료 대기방

- 대기방 정보·참가자 목록·나가기: WebSocket (아래 5번)
- 친구 초대: 엔드포인트 미정 — 구현 후순위 (상세 4번)

### 5. 매칭·러닝 WebSocket — `/ws/running-matches`

연결 1개, 메시지 13종(아래 표). 이 외에 요청 결과로 오가는 **ack 3종**(`MATCH_WAITING`·`RUNNING_STARTED`·`RUNNING_FINISHED`)과 **응답 1종**(`ROOM_PLAYERS`, `GET_ROOM_PLAYERS`의 응답)이 있음 — 상세는 5-A~5-D 본문 참고.

| 그룹 | 메시지 | 방향 | 비고 |
|------|--------|------|------|
| 매칭 중 | `MATCH_REQUEST` | C→S | 매칭 요청 (시각+거리) |
| 매칭 중 | `MATCH_CANCEL` | C→S | 대기 취소 + 확정 후 나가기 겸용(서버가 방 상태로 분기, ack 없음) |
| 매칭 중 | `MATCH_PLAYERS_UPDATED` | S→C | |
| 매칭 방 | `MATCH_STARTED` | S→C | 매칭 성사 통지 |
| 매칭 방 | `MATCH_ROOM_UPDATED` | S→C | `RoomInfo`에 `status` 포함 — 취소 통지도 `status: CANCELLED`로 처리 |
| 러닝 카운트 다운 | `GET_ROOM_PLAYERS` | C→S | 대기방 참여자 조회 |
| 러닝 카운트 다운 | `RUNNING_START` | C→S | 클라 주도 시작 |
| 러닝 중 | `RUNNING_LOCATION_UPDATE` | C→S | 고빈도 — ack 없음 |
| 러닝 중 | `PLAYER_RUNNING_PROGRESS_UPDATED` | S→C | |
| 러닝 중 | `RUNNING_FINISH` | C→S | `forced` 플래그로 강제 종료 포함 — 이 시점에 서버가 `running_records` 저장 |
| 공통 | `ERROR` | S→C | WS 요청 실패 통지 |

### 6. 러닝 중 / 러닝 후 대시보드

| # | Method | Path | 설명 |
|---|--------|------|------|
| 11 | GET | `/api/v1/running-sessions/{runningSessionId}/results` | 참가자 전원 최종 결과 — 사용 화면: 러닝 후 대시보드 |
| 12 | GET | `/api/v1/running-sessions/{runningSessionId}/split-results` | 구간별 상세 + GPS 경로 |

### 7. 기록 화면

| # | Method | Path | 설명 |
|---|--------|------|------|
| 13 | GET | `/api/v1/users/me/running-records` | 내 러닝 기록 목록(기간 필터, 캘린더용) — 사용 화면: 기록, 피드 작성(템플릿 선택) |
| 14 | GET | `/api/v1/running-records/{runningRecordId}` | 기록 상세 (경로·구간 포함) |
| 15 | POST | `/api/v1/running-records/gps/presigned-url` | 솔로 러닝 GPS 트랙 업로드 URL |
| 16 | POST | `/api/v1/running-records` | 솔로 러닝 완주 기록 저장 (매칭 없이 혼자) |

### 8. 대회 화면 [MVP 제외]

| # | Method | Path | 설명 |
|---|--------|------|------|
| 17 | GET | `/api/v1/contests` | 대회 목록 + 검색·필터(날짜/지역/거리). 상세 API 없음 — 목록에 `detailUrl` 포함(공식 홈페이지 이동) |
| 18 | POST | `/api/v1/contests/{contestId}/bookmark` | 일정 추가(북마크) |
| 19 | DELETE | `/api/v1/contests/{contestId}/bookmark` | 북마크 해제 |
| 20 | GET | `/api/v1/users/me/contest-bookmarks` | 북마크한 대회 목록 — 사용 화면: 기록(캘린더 병합), 대회 |

### 9. 피드 목록 페이지 (+댓글 모달) [MVP 제외]

| # | Method | Path | 설명 |
|---|--------|------|------|
| 21 | GET | `/api/v1/feeds` | 피드 목록, `tab=FOLLOWING\|ALL`, 무한 스크롤 |
| 22 | GET | `/api/v1/feeds/{feedId}` | 피드 단건 — 사용 화면: 푸시 랜딩, 검색 결과, 프로필 그리드 탭 |
| 23 | POST | `/api/v1/feeds/{feedId}/like` | 좋아요 (응답에 갱신 카운트) |
| 24 | DELETE | `/api/v1/feeds/{feedId}/like` | 좋아요 취소 |
| 25 | GET | `/api/v1/feeds/{feedId}/comments` | 댓글 목록 (등록순, 답글 제외) |
| 26 | POST | `/api/v1/feeds/{feedId}/comments` | 댓글/답글 작성 (`parentCommentId` 옵션, depth 1 제한) |
| 27 | PATCH | `/api/v1/comments/{commentId}` | 댓글 수정 (작성자 본인만) |
| 28 | GET | `/api/v1/comments/{commentId}/replies` | 답글 지연 로딩 ("답글 N개 보기") |
| 29 | DELETE | `/api/v1/comments/{commentId}` | 댓글 삭제 (작성자 or 피드 소유자, 레딧 방식) |
| 30 | POST | `/api/v1/comments/{commentId}/like` | 댓글 좋아요 |
| 31 | DELETE | `/api/v1/comments/{commentId}/like` | 댓글 좋아요 취소 |

### 10. 피드 작성 페이지 (+프로필의 피드 편집) [MVP 제외]

| # | Method | Path | 설명 |
|---|--------|------|------|
| 33 | POST | `/api/v1/feeds/images/presigned-url` | 피드 이미지 업로드 URL 발급 (여러 장) |
| 34 | POST | `/api/v1/feeds` | 피드 작성 (텍스트/이미지 최소 1, 공개범위, 기록 템플릿 `runningRecordId`) |
| 35 | PATCH | `/api/v1/feeds/{feedId}` | 피드 수정 (내용·공개범위) — 사용 화면: 프로필(피드 편집) |
| 36 | DELETE | `/api/v1/feeds/{feedId}` | 피드 삭제 (소프트delete) |

### 11. 프로필 페이지 (본인/타인)

| # | Method | Path | 설명 |
|---|--------|------|------|
| 37 | GET | `/api/v1/users/me` | 내 기본 정보 — 사용 화면: 전역 |
| 38 | GET | `/api/v1/users/{userId}` | 프로필 요약 (마일리지·최고 페이스·러닝 횟수·친구 수) |
| 39 | GET | `/api/v1/users/{userId}/feeds` | 피드 그리드 (경량: 썸네일+장수) |
| 42 | POST | `/api/v1/users/{userId}/follow` | 팔로우 — 사용 화면: 프로필, 팔로워/팔로잉 목록 |
| 43 | DELETE | `/api/v1/users/{userId}/follow` | 언팔로우 |
| 44 | GET | `/api/v1/users/{userId}/followers` | 팔로워 목록 (+이름 검색) |
| 45 | GET | `/api/v1/users/{userId}/followings` | 팔로잉 목록 (+이름 검색) |
| 32 | GET | `/api/v1/users/search` | 사용자 검색 — 친구 추가 진입점 (`?q=검색어`) |

### 12. 프로필 편집 페이지

| # | Method | Path | 설명 |
|---|--------|------|------|
| 46 | POST | `/api/v1/users/me/profile-image/presigned-url` | 프로필 사진 업로드 URL 발급 |
| 47 | PATCH | `/api/v1/users/me` | 사진 key·닉네임(409)·인사말 변경 |

### 13. 설정 페이지

| # | Method | Path | 설명 |
|---|--------|------|------|
| 48 | GET | `/api/v1/users/me/settings` | 알림 on/off(단일) 조회 — 공개범위 설정 2차 |
| 49 | PATCH | `/api/v1/users/me/settings` | 설정 변경 |
| 50 | DELETE | `/api/v1/users/me` | 회원탈퇴 (스냅샷→하드delete, 테이블별 정책) |

**합계: REST 50개 + WebSocket 채널 1개(메시지 13종)**

---

# 상세 명세

## 0. 공통 규칙

- **인증**: `Authorization: Bearer {accessToken}` 헤더. Access+Refresh 토큰 이원화, **refresh rotation** — 재발급 시 accessToken·refreshToken 모두 교체(이전 refreshToken 무효). refreshToken은 **바디 전달 + 클라 Keychain/Keystore 보관**. **로그아웃 시 해당 access 토큰은 서버 차단(블랙리스트)**
- **페이지네이션 limit**: `?limit=` 생략 시 기본 **20**, 최대 **50**(초과 요청은 50으로 클램프)
- **시각**: 시점은 ISO 8601 **`yyyy-MM-ddTHH:mm:ss`**(예: `2026-07-20T13:00:00`) — **KST 기준, 타임존 오프셋 없이 초 단위까지**. 클라이언트는 이 값을 KST로 해석한다. 달력 날짜(생일·대회 일정)는 `YYYY-MM-DD`
- **단위**: **거리는 전부 미터, 페이스는 초/km 정수**(`390` → "6:30") — 표시 변환은 프론트 몫(DB에 km로 저장된 값도 API에선 미터)
- **토글 액션**: POST(등록)/DELETE(취소) 분리, idempotent(중복 호출 시 에러 없이 성공 응답) — 팔로우·좋아요는 갱신 상태·카운트 포함 `200 OK`, 대회 북마크는 `204 No Content`
- **enum**: DB·API **동일한 영문 코드**(변환 매핑 없음) — 값 목록은 `erd.md` §6(enum 사전)
- **이미지 업로드 공통(Presigned)**: ① 업로드 URL 발급 API → ② 클라가 S3에 직접 업로드 → ③ 반환받은 `key`(또는 완료 API)를 본 API에 전달
- **탈퇴 유저 작성자 표시**: `{ "userId": "550e8400-...", "nickname": "탈퇴한 사용자", "profileImageUrl": null, "isDeleted": true }` (고정 문구, `userId`는 UUID 문자열 유지)
- **`[MVP 제외]` 표기**: 지금 만들지 않는 엔드포인트. 정의는 그대로 두어 확장 시점에 재작성 없이 쓴다. 마커가 없으면 만드는 것이며, 차수(1차·2차)는 적지 않는다.
- **ID 타입 규칙**: `userId` = **UUID 문자열** (ERD `users.user_id`가 UUID). 그 외 리소스 ID(`runningSessionId`, `feedId`, `commentId`, `contestId`, `runningRecordId`, `badgeId` 등) = **Long**

### 공통 에러 응답

인증 필요(`인증: 필요`) API → **401**, 모든 API → **400**·**500** 공통 발생. 각 엔드포인트 명세엔 특유 에러만 표기.

- **에러 (401 Unauthorized — 인증 실패)**

```json
{
  "code": "TOKEN_EXPIRED",
  "message": "액세스 토큰이 만료되었습니다."
}

{
  "code": "TOKEN_BLOCKED",
  "message": "로그아웃된 액세스 토큰입니다."
}

{
  "code": "INVALID_TOKEN",
  "message": "유효하지 않은 토큰입니다."
}

{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "인증이 필요합니다."
}
```

- **에러 (400 Bad Request — 요청 검증)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "입력값이 올바르지 않습니다."
}

{
  "code": "MALFORMED_REQUEST_BODY",
  "message": "요청 본문을 읽을 수 없습니다."
}
```

- **에러 (500 Internal Server Error)**

```json
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "서버 오류가 발생했습니다."
}
```

## 1. 인증·온보딩

### 1-1. `POST /api/v1/auth/email/verifications` — 이메일 인증번호 발급

로컬 회원가입 3단계(인증번호 발급 → 인증번호 확인 → 가입) 중 1단계. 입력한 이메일로 **6자리 숫자 인증 코드**를 메일 발송.

- **Request**

```json
{
  "email": "example@example.com"   // 필수
}
```

- **Response `204 No Content`** — 본문 없음

- **에러 (400 Bad Request)** — 검증 실패 시 `code`는 `INVALID_REQUEST` 공통, `message`로 사유 구분

```json
{
  "code": "INVALID_REQUEST",
  "message": "올바른 이메일 형식이 아닙니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "이메일은 필수입니다."
}
```

- **에러 (409 Conflict)**

```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "이미 가입된 이메일입니다. 로그인해 주세요."
}
```

- **에러 (429 Too Many Requests)**

```json
{
  "code": "EMAIL_VERIFICATION_COOLDOWN",
  "message": "인증 메일을 방금 보냈습니다. 잠시 후 다시 시도해 주세요."
}

{
  "code": "EMAIL_VERIFICATION_DAILY_LIMIT_EXCEEDED",
  "message": "하루 인증 메일 발송 횟수를 초과했습니다."
}
```

- **에러 (503 Service Unavailable)**

```json
{
  "code": "EMAIL_SEND_FAILED",
  "message": "인증 메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요."
}
```

- **인증**: 불필요

### 1-2. `POST /api/v1/auth/email/verifications/confirm` — 이메일 인증번호 확인

메일로 받은 코드를 검증하고, 회원가입에 쓸 **인증 티켓(`verificationTicket`)** 을 발급.

- **Request**

```json
{
  "email": "example@example.com",   // 필수
  "code": "123456"                  // 필수 — 6자리 숫자(^\d{6}$), 공백 불가
}
```

- **Response `200 OK`**

```json
{
  "verificationTicket": "_YUW5lsbzTgNYp8-B6p73LnLjP6a4YgWlcQnaauHwhc"
}
```

- `verificationTicket`: 회원가입에 사용할 인증 티켓 (URL-safe Base64, 43자). 발급 후 **30분** 유효, **1회용**

- **에러 (400 Bad Request)** — 검증 실패 시 `code`는 `INVALID_REQUEST` 공통, `message`로 사유 구분. 인증 코드 자체의 실패는 별도 `code`

```json
{
  "code": "INVALID_REQUEST",
  "message": "올바른 이메일 형식이 아닙니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "이메일은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "인증 코드는 6자리 숫자입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "인증 코드는 필수입니다."
}

{
  "code": "EMAIL_VERIFICATION_NOT_FOUND",
  "message": "인증 코드가 만료되었습니다. 다시 요청해 주세요."
}

{
  "code": "INVALID_VERIFICATION_CODE",
  "message": "인증 코드가 올바르지 않습니다."
}
```

- **에러 (429 Too Many Requests)**

```json
{
  "code": "TOO_MANY_VERIFICATION_ATTEMPTS",
  "message": "인증 시도 횟수를 초과했습니다. 코드를 다시 요청해 주세요."
}
```

- **인증**: 불필요

### 1-3. `POST /api/v1/auth/signup` — 로컬 회원가입

이메일 인증(1-1 → 1-2)으로 받은 티켓으로 가입. 이메일은 티켓에서 확인한 값을 쓰므로 요청에 담지 않는다.

- **Request**

```json
{
  "verificationTicket": "_YUW5lsbzTgNYp8-B6p73LnLjP6a4YgWlcQnaauHwhc",   // 필수 — 인증 확인 API에서 받은 티켓 원문
  "password": "********"                                                  // 필수 — 6~16자, 영문·숫자·특수문자 각 1자 이상 (확인 일치 검증은 클라이언트)
}
```

- **Response `201 Created`** — **자동 로그인** (로그인과 동일 형태로 토큰 발급)

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "accessToken": "ey...",
  "refreshToken": "ey...",
  "isOnboarded": false
}
```

- **에러 (400 Bad Request)** — 검증 실패 시 `code`는 `INVALID_REQUEST` 공통, `message`로 사유 구분

```json
{
  "code": "INVALID_REQUEST",
  "message": "이메일 인증 티켓은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 6자 이상 16자 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 필수입니다."
}
```

- **에러 (403 Forbidden)**

```json
{
  "code": "EMAIL_NOT_VERIFIED",
  "message": "이메일 인증이 만료되었습니다. 다시 인증해 주세요."
}
```

- **에러 (409 Conflict)**

```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "이미 가입된 이메일입니다. 로그인해 주세요."
}
```

- **인증**: 불필요

### 1-4. `POST /api/v1/auth/login` — 로컬 로그인

- **Request** (둘 다 필수)

```json
{
  "email": "...",
  "password": "..."
}
```

- **이메일 대소문자**: 서버는 입력값을 그대로 조회한다 — 가입 시 소문자로 정규화해 저장하므로 **클라이언트가 소문자로 변환해 보낸다**. 대문자가 섞이면 `401 INVALID_CREDENTIALS`

- **Response `200 OK`**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "accessToken": "ey...",
  "refreshToken": "ey...",
  "isOnboarded": false
}
```

- 닉네임 등 상세는 `GET /users/me`

- **에러 (400 Bad Request)** — 검증 실패 시 `code`는 `INVALID_REQUEST` 공통, `message`로 사유 구분

```json
{
  "code": "INVALID_REQUEST",
  "message": "올바른 이메일 형식이 아닙니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "이메일은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 필수입니다."
}
```

- **에러 (401 Unauthorized — 이메일/비밀번호 불일치)**

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 일치하지 않습니다."
}
```

- **인증**: 불필요

### 1-5. `POST /api/v1/auth/oauth/google` / 1-6. `POST /api/v1/auth/oauth/kakao` — 소셜 로그인 (인가 코드 방식)

- **Request** (둘 다 필수, 구글·카카오 공통)

```json
{
  "authorizationCode": "...",
  "codeVerifier": "..."
}
```

- **동작**: 서버가 provider에 인가 코드 교환(PKCE `codeVerifier` 검증) → 유저 정보 조회 → `provider_id`로 `oauth_users` 조회, 없으면 생성(회원가입) → 자체 토큰 발급
- **Response `200 OK`**: 1-4 로그인과 동일 형태 (`userId`/`accessToken`/`refreshToken`/`isOnboarded`) — 최초 가입 여부와 무관하게 토큰 발급
- **에러 (401 Unauthorized — 코드 교환 실패 — 위조·만료·PKCE 불일치)**

```json
{
  "code": "OAUTH_CODE_EXCHANGE_FAILED",
  "message": "소셜 로그인에 실패했습니다. 다시 시도해 주세요."
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "인가 코드는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "코드 검증값은 필수입니다."
}

{
  "code": "UNSUPPORTED_PROVIDER",
  "message": "지원하지 않는 로그인 제공자입니다."
}
```

- **에러 (403 Forbidden — 카카오 이메일 제공 미동의 — 가입 거부)**

```json
{
  "code": "OAUTH_EMAIL_NOT_PROVIDED",
  "message": "이메일 제공에 동의해야 소셜 로그인을 할 수 있습니다."
}
```

- **에러 (409 Conflict — 소셜 최초 가입인데 이메일이 기존 로컬 계정과 겹침)** — 자동 연동하지 않는다. 클라는 로컬 로그인으로 안내

```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "이미 가입된 이메일입니다. 로그인해 주세요."
}
```

- **인증**: 불필요

### 1-7. `POST /api/v1/auth/refresh` — 토큰 재발급

- **Request**: `{ "refreshToken": "ey..." }` (필수)
- **Response `200 OK`**

```json
{
  "accessToken": "ey...",
  "refreshToken": "ey..."
}
```

- 클라는 accessToken·refreshToken 둘 다 갱신 저장
- **에러 (400 Bad Request — 리프레시 토큰 누락)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "리프레시 토큰은 필수입니다."
}
```

- **에러 (401 Unauthorized — 만료·위조 → 재로그인 유도)**

```json
{
  "code": "INVALID_REFRESH_TOKEN",
  "message": "리프레시 토큰이 유효하지 않습니다. 다시 로그인해 주세요."
}
```

- **인증**: 불필요 (refreshToken 자체가 자격증명)

### 1-8. `POST /api/v1/auth/logout` — 로그아웃

- **Request**: 본문 없음 — 서버가 요청 토큰으로 본인 식별. **해당 access 토큰을 서버 차단(블랙리스트)** 처리해 만료 전이라도 무효화 (이후 그 토큰 요청은 `401 TOKEN_BLOCKED`)
- **Response**: `204 No Content`

- **인증**: 필요

### 1-9. `POST /api/v1/users/onboarding` — 온보딩 입력

- **Request** (전부 필수)

```json
{
  "nickname": "완두콩",
  "gender": "MALE",                  // MALE | FEMALE
  "birthday": "1998-12-16",
  "averagePaceSecondsPerKm": 359,    // 초/km 정수 (5'59") — 초기값. 이후 러닝 기록 기반 서버 자동 갱신 (수정 UI 없음)
  "weightKg": 77,
  "heightCm": 175
}
```

- **약관 동의**: 별도 요청 필드 없음 — 온보딩 완료(=`user_onboardings` row 생성)가 동의로 갈음, 동의 시각 증빙 = `user_onboardings.created_at`

- **Response `201 Created`**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "nickname": "완두콩"
}
```

- **에러 (409 Conflict)**

```json
{
  "code": "NICKNAME_ALREADY_EXISTS",
  "message": "이미 사용 중인 닉네임입니다."
}

{
  "code": "ALREADY_ONBOARDED",
  "message": "이미 온보딩을 완료한 계정입니다."
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 2자 이상 16자 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "성별은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "성별은 MALE 또는 FEMALE이어야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "생년월일은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "생년월일은 미래일 수 없습니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "생년월일은 1900년 1월 1일 이후여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "평균 페이스는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "평균 페이스는 120초 이상이어야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "평균 페이스는 1800초 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "몸무게는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "몸무게는 20kg 이상이어야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "몸무게는 300kg 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "키는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "키는 20cm 이상이어야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "키는 300cm 이하여야 합니다."
}
```

- **인증**: 필요

## 2. 공통 — 디바이스/푸시

### 2-1. `POST /api/v1/devices` — 디바이스 등록/갱신

- **화면**: 로그인 직후 전역 (푸시 수신 준비)
- **Request**

```json
{
  "pushToken": "fcm-token-...",   // 필수
  "platform": "IOS",              // 필수 — IOS | ANDROID
  "deviceId": "device-uuid-...",  // 필수 — 기기 고유 식별자
  "appVersion": "1.0.0"           // 선택
}
```

- **동작**: `deviceId` 기준 upsert(없으면 생성)
- **Response**: `204 No Content`

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "입력값이 올바르지 않습니다."
}
```

- **인증**: 필요

## 3. 홈 화면 — 날씨

**서버 API 없음**: 홈 화면 날씨는 클라이언트가 **키 없는 무료 날씨 API(예: Open-Meteo)를 직접 호출** — 앱에 키를 심지 않아 보안 부담이 없고 서버 개발·운영 대상에서 제외. 유료 전환·서버 캐싱이 필요해지면 프록시 API 추가(하위호환).

## 4. 친구 초대

MVP 범위이나 **구현 순서상 후순위** — 랜덤 매칭이 동작한 뒤에 붙인다. **엔드포인트 미정.**

초대받은 사람은 `running_players.status='INVITED'`로 생성되고, 수락하면 `CONFIRMED`, 거절하면 row를 DELETE한다(거절 이력 보관 안 함).

## 5. 매칭·러닝 WebSocket — `/ws/running-matches`

- **연결**: `wss://.../ws/running-matches` + `Authorization: Bearer {accessToken}` (연결 1개로 매칭→대기방→카운트다운→러닝 전 구간 처리)
- **메시지 공통 형식**

```json
{
  "type": "...",
  "data": { ... }
}
```

- **ack 규칙**: 상태가 걸린 요청에만 — `MATCH_REQUEST`→`MATCH_WAITING`, `RUNNING_START`→`RUNNING_STARTED`, `RUNNING_FINISH`→`RUNNING_FINISHED`
  - **`MATCH_CANCEL`·`RUNNING_LOCATION_UPDATE`는 ack 없음**(보내고 끝 — 실패는 `ERROR`)
  - ack의 `data`는 비움
- **`ERROR` (S→C)** — WS 요청 실패 통지. REST 에러 포맷과 동일 계열

```json
{
  "code": "SESSION_NOT_FOUND",
  "message": "러닝 세션을 찾을 수 없습니다.",
  "sourceType": "RUNNING_LOCATION_UPDATE"
}
```

- **code**: `INVALID_REQUEST`(요청 검증 실패) / `SESSION_NOT_FOUND`(세션 없음) / `NOT_SESSION_PLAYER`(참가자 아님) / `INVALID_SESSION_STATE`(현재 상태에서 불가한 요청) / `ALREADY_MATCHING`(이미 매칭 대기·방에 있는데 재요청)

- **DB row 트리거** — `running_room_sessions`은 방↔플레이어 순수 연결 테이블
  - 링크 생성 = 방 배정 시(`MATCH_REQUEST` 처리)
  - `MATCH_CANCEL` 수신 시 서버가 방 상태로 분기 — 대기 중(`MATCHING`)이면 `running_players`와 링크 DELETE, 확정 후(`MATCHED`)면 **둘 다 유지 + `status=LEFT`**(어느 방에서 나갔는지가 페널티·이력 근거)
  - 방 자동 취소 시 전원 유지. 원칙: "확정 전엔 지우고, 확정 후엔 남긴다"

### 5-A. 매칭 중 (홈 → 매칭 대기 화면)

#### `MATCH_REQUEST` (C→S) — 러닝 매칭 요청

```json
{
  "scheduledStartAt": "2026-07-25T10:00:00",  // 희망 시작 시각
  "targetDistanceMeters": 5000                  // 목표 거리(m)
}
```

- 모든 방은 공개 랜덤 매칭 — 프라이빗 방 없음
- 페이스 조건은 입력받지 않음 — 서버가 보관한 사용자 평균 페이스 자동 사용 (온보딩 입력값에서 시작, 이후 러닝 기록 기반 자동 갱신)
- **모집 인원도 입력받지 않음** — 서버가 2~4명 범위에서 자동 편성 (`desiredMemberCount` 필드 없음)
- **ack**: `MATCH_WAITING` (매칭 대기 진입)

#### `MATCH_CANCEL` (C→S) — 매칭 취소·방 나가기 (겸용)

- Data: 없음 (연결 컨텍스트로 본인 처리)
- **서버가 방 상태로 분기**
  - 대기 중(`MATCHING`) = 대기 취소(row 삭제)
  - 확정 후(`MATCHED`) = 이탈(`LEFT` 처리, 페널티 대상)
  - 남은 인원에 겐 `MATCH_PLAYERS_UPDATED` 또는 `MATCH_ROOM_UPDATED`로 갱신, 이탈로 2명 미만이면 `status: CANCELLED` 통지
- **ack 없음** — 보내고 화면 닫으면 끝, 실패는 `ERROR`

#### `MATCH_PLAYERS_UPDATED` (S→C) — 매칭 참여자 갱신

```json
{
  "runningSessionId": 125,
  "players": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440015",
      "nickname": "동완러너",
      "profileImageUrl": "..."
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440013",
      "nickname": "철수",
      "profileImageUrl": "..."
    }
  ]
}
```

- 매칭 무산(confirm_deadline 시점 2명 미만)·방 취소 통지: 별도 메시지 없음 — **`MATCH_ROOM_UPDATED`의 `status: "CANCELLED"`**로 전달. 수신 시 클라는 홈으로

### 5-B. 매칭 방 (매칭완료 대기방)

#### 공통 객체 `RoomInfo` — 매칭방 전체 정보

`MATCH_STARTED`와 `MATCH_ROOM_UPDATED`의 `data`는 아래 **동일 구조를 공유** (정의 한 곳 — 서버도 같은 직렬화 재사용):

```json
{
  "runningSessionId": 125,
  "status": "MATCHED",               // running_rooms.status: MATCHING|MATCHED|STARTED|FINISHED|CANCELLED — CANCELLED면 클라는 홈으로
  "scheduledStartAt": "2026-07-25T10:00:00",
  "targetDistanceMeters": 5000,
  "teamAveragePaceSecondsPerKm": 375,
  "players": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440015",
      "nickname": "동완러너",
      "status": "CONFIRMED",              // PlayerStatus: INVITED | CONFIRMED | LEFT
      "profileImageUrl": "https://...",
      "introduction": "즐겁게 같이 달려요!",   // users.introduction
      "averagePaceSecondsPerKm": 360
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440013",
      "nickname": "철수",
      "status": "CONFIRMED",
      "profileImageUrl": "https://...",
      "introduction": "천천히 오래 달려요.",
      "averagePaceSecondsPerKm": 390
    }
  ]
}
```

#### `MATCH_STARTED` (S→C) — 매칭 성사 통지

- `data` = `RoomInfo`. 수신 시 클라는 대기 화면 → 매칭방 화면으로 전환

#### `MATCH_ROOM_UPDATED` (S→C) — 매칭방 정보 갱신

- `data` = `RoomInfo` 전체 재전송 — 참가자 입장/퇴장/취소로 목록·팀 평균 페이스가 바뀔 때

#### 방 나가기 — 별도 메시지 없음

- 확정된 방에서 나가기도 **`MATCH_CANCEL`** 사용 (5-A 참고 — 서버가 방 상태로 분기)
- 나간 사람만 `LEFT` 처리, 방 유지. 남은 인원은 `MATCH_ROOM_UPDATED`로 갱신, 이탈로 2명 미만이면 `status: CANCELLED`
- 확정 후 이탈 페널티는 미설계

### 5-C. 러닝 카운트 다운

#### `GET_ROOM_PLAYERS` (C→S) — 대기방 참여자 조회

- 매칭방 참여자 **프로필 표시**용 조회(채팅 등 추후) — 대기열 인원 현황(`~명 대기중`)은 `MATCH_PLAYERS_UPDATED`로 구분

```json
{
  "runningSessionId": 125
}
```

- **응답**: `ROOM_PLAYERS`

```json
{
  "players": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440015",
      "nickname": "러닝초보",
      "profileImageUrl": "..."
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440013",
      "nickname": "철수",
      "profileImageUrl": "..."
    }
  ]
}
```

#### `RUNNING_START` (C→S) — 러닝 시작 알림 (클라 주도)

```json
{
  "runningSessionId": 125
}
```

- 카운트다운은 클라 자체 시계 기준 — `scheduledStartAt` 도달 시 클라가 러닝 화면 전환 + 이 메시지 발신. 서버는 같은 시각에 스케줄러로 방 상태 `STARTED` 전환
- **ack**: `RUNNING_STARTED`

### 5-D. 러닝 중

#### `RUNNING_LOCATION_UPDATE` (C→S) — 위치 정보 전송 (주기 발신)

```json
{
  "runningSessionId": 125,
  "location": {
    "sequence": 15,                    // Long, 세션 내 순번
    "latitude": 35.1795543,            // -90~90
    "longitude": 129.0756416,          // -180~180
    "altitudeMeters": 18.4,            // m
    "accuracyMeters": 6.2,             // GPS 수평 오차 반경 m
    "speedMetersPerSecond": 2.8,
    "headingDegrees": 85.3,            // 0~360
    "cadenceSpm": 165,
    "currentPaceSecondsPerKm": 345,
    "recordedAt": "2026-07-25T10:10:30"   // 측정 시각
  }
}
```

- 서버는 Redis(`sessionId+userId` 키)에 버퍼링 — 종료 시 S3 업로드(`gpsTrackKey`)
- **ack 없음** — 초 단위 고빈도 메시지라 건별 ack는 트래픽 낭비. 실패는 `ERROR`로 통지

#### `PLAYER_RUNNING_PROGRESS_UPDATED` (S→C) — 참여자 진행 정보

```json
{
  "runningSessionId": 125,
  "players": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440015",
      "profileImageUrl": "https://...",
      "distanceMeters": 1520,               // 현재까지 이동 거리
      "targetDistanceMeters": 5000,         // 목표 거리(m)
      "currentPaceSecondsPerKm": 345        // 현재 페이스(초/km)
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440013",
      "profileImageUrl": "https://...",
      "distanceMeters": 1360,
      "targetDistanceMeters": 5000,
      "currentPaceSecondsPerKm": 372
    }
  ]
}
```

#### `RUNNING_FINISH` (C→S) — 러닝 종료 (정상/강제 통합)

```json
{
  "runningSessionId": 125,
  "forced": false
}
```

- `forced=true` = 목표 도달 전 즉시 종료 — 정상/강제의 서버 처리(현재까지 데이터로 기록 저장 + 세션 종료)가 동일해 플래그로만 구분
- **이 시점에 서버가 `running_records`(+splits) 저장**. GPS 트랙은 서버가 S3 업로드 + 다운샘플 `route_polyline` 생성(피드 카드용)
- **ack**: `RUNNING_FINISHED` — 수신 후 클라는 REST `GET /running-sessions/{id}/results`로 대시보드 진입
- 전원 제출 완료 or 타임아웃 중 먼저 오는 시점에 방 상태 `FINISHED` (타임아웃 값은 운영 정책)

## 6. 러닝 중 / 러닝 후 대시보드 (REST)

> **러닝 사진**: 앱에서 촬영해 디바이스 갤러리에만 저장 — 서버 업로드/조회 API 없음. results 등 응답에 사진 필드 없음.

### 6-1. `GET /api/v1/running-sessions/{runningSessionId}/results` — 러닝 종료 결과 (참가자 전원 요약)

- **화면**: 러닝 후 - 대시보드 (참가자 공통 정보). `RUNNING_FINISHED` 수신 후 진입
- **Response `200 OK`**

```json
{
  "runningSessionId": 125,
  "startedAt": "2026-07-25T10:00:30",
  "finishedAt": "2026-07-25T10:30:30",
  "players": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440015",
      "nickname": "동완러너",
      "profileImageUrl": "https://...",   // nullable
      "isMe": true,
      "totalDistanceMeters": 5020,
      "durationSeconds": 1800,
      "caloriesKcal": 352,
      "averagePaceSecondsPerKm": 359,
      "averageCadenceSpm": 165,
      "totalElevationGainMeters": 42
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440028",
      "nickname": "부산러너",
      "profileImageUrl": "https://...",
      "isMe": false,
      "totalDistanceMeters": 5000,
      "durationSeconds": 1750,
      "caloriesKcal": 340,
      "averagePaceSecondsPerKm": 350,
      "averageCadenceSpm": 172,
      "totalElevationGainMeters": 35
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440031",
      "nickname": "러닝초보",
      "profileImageUrl": null,
      "isMe": false,
      "totalDistanceMeters": 4870,
      "durationSeconds": 1800,
      "caloriesKcal": 315,
      "averagePaceSecondsPerKm": 370,
      "averageCadenceSpm": 158,
      "totalElevationGainMeters": 28
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440001",
      "nickname": "완두콩",
      "profileImageUrl": "https://...",
      "isMe": false,
      "totalDistanceMeters": 5010,
      "durationSeconds": 1765,
      "caloriesKcal": 344,
      "averagePaceSecondsPerKm": 352,
      "averageCadenceSpm": 168,
      "totalElevationGainMeters": 38
    }
  ]
}
```

- 미제출(미완주) 참가자는 목록에서 제외되거나 부분 데이터일 수 있음

- **에러 (403 Forbidden — 같은 방 참가자만 열람)**

```json
{
  "code": "NOT_SESSION_PLAYER",
  "message": "같은 세션 참가자만 조회할 수 있습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요 (같은 세션 참가자)

### 6-2. `GET /api/v1/running-sessions/{runningSessionId}/split-results` — 구간별 상세 + GPS 경로

- **화면**: 러닝 후 - 대시보드 (본인 경로 확인 + 참가자 상세·구간별 비교)
- **Response `200 OK`** (구조 요약)

```json
{
  "runningSessionId": 125,
  "splitDistanceMeters": 1000,          // 기본 구간 거리
  "totalDistanceMeters": 5020,          // 현재 사용자 총 거리
  "startedAt": "2026-07-25T10:00:30",
  "finishedAt": "2026-07-25T10:30:30",
  "route": {                             // 현재 사용자의 전체 경로
    "startLocation": {
      "latitude": 35.1795543,
      "longitude": 129.0756416
    },
    "endLocation": {
      "latitude": 35.1842012,
      "longitude": 129.0831421
    },
    "gpsPoints": [
      {
        "sequence": 1,
        "latitude": 35.1795543,
        "longitude": 129.0756416,
        "altitudeMeters": 18.4,
        "accuracyMeters": 6.2,
        "currentPaceSecondsPerKm": 345,
        "cadenceSpm": 165,
        "recordedAt": "2026-07-25T10:00:30"
      }
    ]
  },
  "splits": [
    {
      "splitNumber": 1,                  // 1부터 시작
      "startDistanceMeters": 0,
      "endDistanceMeters": 1000,
      "distanceMeters": 1000,            // 마지막 구간은 1000 미만일 수 있음
      "startPoint": {
        "latitude": 35.1795543,
        "longitude": 129.0756416
      },
      "players": [
        {
          "userId": "550e8400-e29b-41d4-a716-446655440015",
          "nickname": "동완러너",
          "profileImageUrl": "https://...",
          "isMe": true,
          "durationSeconds": 345,
          "averagePaceSecondsPerKm": 345,
          "averageCadenceSpm": 162,
          "caloriesKcal": 68,
          "elevationGainMeters": 8
        }
      ]
    }
  ]
}
```

- 아직 안 끝난(미완주) 유저는 해당 구간에 정보가 없을 수 있음

- **에러 (403 Forbidden)**

```json
{
  "code": "NOT_SESSION_PLAYER",
  "message": "같은 세션 참가자만 조회할 수 있습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요 (같은 세션 참가자)

## 7. 기록 화면

### 7-1. `GET /api/v1/users/me/running-records` — 내 러닝 기록 목록

- **화면**: 기록(캘린더 — 북마크 대회 API와 클라이언트가 날짜 기준 병합), 피드 작성(러닝기록 템플릿 선택)
- **Query**: `from`/`to`(ISO date, 캘린더 월 조회용) 또는 `cursor`/`limit`(최근순 목록용 — 템플릿 선택 모달)
- **Response `200 OK`**

```json
{
  "items": [
    {
      "runningRecordId": 501,
      "runningSessionId": 125,           // null = 솔로 러닝(매칭 없이 혼자)
      "startedAt": "2026-07-25T10:00:30",
      "totalDistanceMeters": 5020,
      "durationSeconds": 1800,
      "averagePaceSecondsPerKm": 359
    }
  ],
  "nextCursor": null
}
```

- **인증**: 필요 (본인 기록만)

### 7-2. `GET /api/v1/running-records/{runningRecordId}` — 기록 상세

- **화면**: 기록(일정 상세 — 경로·러닝 기록)
- **Response `200 OK`**: 7-1 필드 + `finishedAt`, `averageCadenceSpm`, `caloriesKcal`, `totalElevationGainMeters`, `route`(6-2와 동일 구조 — 본인 GPS 경로), `splits`(본인 구간 기록: `splitNumber`/`distanceMeters`/`durationSeconds`/`averagePaceSecondsPerKm` 등)
- 같은 방 참가자 비교는 6-1·6-2(세션 API) 사용 — 이 API는 **본인 기록 전용**

- **에러 (403 Forbidden — 본인 기록 아님)**

```json
{
  "code": "FORBIDDEN",
  "message": "권한이 없습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요 (본인)

> **솔로 러닝 = `runningSessionId: null`** — 매칭 없이 혼자 뛴 기록. 7-1·7-2 응답의 `runningSessionId`는 nullable(매칭 러닝만 값 존재). 7-1/7-2는 매칭·솔로 공통 조회.

### 7-3. `POST /api/v1/running-records/gps/presigned-url` — 솔로 GPS 트랙 업로드 URL

- **화면**: 솔로 러닝 종료 직후 (클라가 로컬 추적한 GPS 트랙을 업로드). 매칭 러닝은 서버가 Redis 버퍼→S3로 저장하므로 이 API 불필요 — **솔로 전용**
- **Request**

```json
{
  "originalFileName": "track.json",
  "mimeType": "application/json"
}
```

- **Response `200 OK`** — 클라는 `uploadUrl`로 S3에 직접 업로드

```json
{
  "gpsTrackKey": "gps/2026/07/uuid.json",
  "uploadUrl": "https://..."
}
```

- **업로드 파일 포맷**: 6-2 `route.gpsPoints[]`와 **동일 구조**(`sequence`/`latitude`/`longitude`/`altitudeMeters`/`accuracyMeters`/`currentPaceSecondsPerKm`/`cadenceSpm`/`recordedAt`) — 서버가 그대로 읽어 7-2 `route`로 반환
- **인증**: 필요

### 7-4. `POST /api/v1/running-records` — 솔로 러닝 완주 기록 저장

- **화면**: 솔로 러닝 종료 → 결과 저장. 매칭 러닝은 서버가 WS `RUNNING_FINISH` 때 저장하므로 이 API는 **솔로 전용**(충돌 없음)
- **시작 알림 없음**: 솔로는 알릴 상대가 없어 시작 API 불필요 — 클라가 로컬로 추적(카운트다운·시계 모두 클라)
- **Request** (`gpsTrackKey`·좌표 필수 — 야외 GPS 러닝만. 실내/트레드밀은 **[MVP 제외]**)

```json
{
  "startedAt": "2026-07-26T07:00:00",
  "finishedAt": "2026-07-26T07:32:10",
  "totalDistanceMeters": 5020,
  "durationSeconds": 1930,
  "averagePaceSecondsPerKm": 384,
  "averageCadenceSpm": 172,
  "caloriesKcal": 320,
  "totalElevationGainMeters": 45,
  "startLatitude": 35.1795543,
  "startLongitude": 129.0756416,
  "endLatitude": 35.1842012,
  "endLongitude": 129.0831421,
  "gpsTrackKey": "gps/2026/07/uuid.json",
  "routePolyline": "u{~vFvyys@fS]pT_@...",
  "splits": [
    {
      "splitNumber": 1,
      "distanceMeters": 1000,
      "durationSeconds": 380,
      "averagePaceSecondsPerKm": 380,
      "startLatitude": 35.1795543,
      "startLongitude": 129.0756416,
      "startedAt": "2026-07-26T07:00:00",
      "finishedAt": "2026-07-26T07:06:20",
      "averageCadenceSpm": 170,
      "caloriesKcal": 65,
      "elevationGainMeters": 10
    }
  ]
}
```

- **필수**: `startedAt`, `finishedAt`, `totalDistanceMeters`, `durationSeconds`, `averagePaceSecondsPerKm`, `startLatitude/Longitude`, `endLatitude/Longitude`, `gpsTrackKey`, `routePolyline`, `splits`
- **`routePolyline`**: 전체 경로를 다운샘플한 encoded polyline(피드 카드 지도 미리보기용 → `running_records.route_polyline`). 매칭 러닝은 서버가 Redis 버퍼로 생성하므로 솔로만 클라 제출 — 포인트 수 등 다운샘플 정책은 운영값
- **splits 항목별 필수**: `splitNumber`, `distanceMeters`, `durationSeconds`, `averagePaceSecondsPerKm`, `startLatitude/Longitude`(구간 시작점 → `running_splits.session_lat/lng`), `startedAt`/`finishedAt`(구간 시작/종료 시각 → `session_start_date/session_end_date`) — 매칭 러닝은 서버가 Redis 버퍼로 직접 채우는 값이라 솔로만 클라 제출
- **선택**: `averageCadenceSpm`, `caloriesKcal`, `totalElevationGainMeters` (구간별 동일)
- **동작**: 서버가 `running_records`(`running_room_id=null`) + `running_splits` 생성. `gpsTrackKey`가 S3에 존재하는지 검증
- **Response `201 Created`**: 7-2 상세 형식 (`runningSessionId: null`)

- **에러 (400 Bad Request — 입력 불량)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "입력값이 올바르지 않습니다."
}
```

- **에러 (409 Conflict — `gpsTrackKey` S3에 없음)**

```json
{
  "code": "UPLOAD_NOT_FOUND",
  "message": "업로드된 파일을 찾을 수 없습니다."
}
```

- **인증**: 필요
- ⚠️ 솔로는 서버가 실측을 못 해 **클라 제출값을 그대로 저장** — 리더보드·페널티 도입 시 GPS 트랙 기반 검증 추가 여지

## 8. 대회 화면 [MVP 제외]

### 8-1. `GET /api/v1/contests` — 대회 목록·검색·필터

- **화면**: 대회 (검색 + 날짜/지역/거리 필터). 상세 페이지 없음 — `detailUrl`로 공식 홈페이지 이동
- **Query**: `q`(대회명 검색), `region`, `dateFrom`/`dateTo`(개최일 범위), `distanceMeters`(예: `10000`), `cursor`/`limit` — 전부 선택
- **Response `200 OK`**

```json
{
  "items": [
    {
      "contestId": 42,
      "name": "부산 마라톤 2026",
      "region": "부산",
      "venue": "광안리 해변",
      "eventDate": "2026-10-15",
      "distancesMeters": [5000, 10000, 21097.5, 42195],
      "thumbnailImageUrl": "https://...",
      "registrationStartDate": "2026-08-01",
      "registrationEndDate": "2026-09-30",
      "detailUrl": "https://busanmarathon.example.com",
      "isBookmarked": false
    }
  ],
  "nextCursor": "..."
}
```

- **날짜 필드**: `eventDate`·`registrationStartDate`·`registrationEndDate`는 전부 **Date(`YYYY-MM-DD`, 시각·시간대 없음)** — 대회는 달력 일정이라 §0 "달력 날짜=Date" 규칙 적용. 클라는 변환 없이 그대로 표시.
- **인증**: 필요

### 8-2. `POST /api/v1/contests/{contestId}/bookmark` — 일정 추가(북마크) / 8-3. `DELETE` — 해제

- **동작**: `user_running_contests` row 생성/삭제 (단순 연결 — 참가 상태값 없음). idempotent
- 북마크한 대회는 "대회 접수 시작" 푸시 대상이 됨
- **Response**: `204 No Content`

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 8-4. `GET /api/v1/users/me/contest-bookmarks` — 북마크한 대회 목록

- **화면**: 기록(캘린더 병합용), 대회(북마크 탭/표시)
- **Response `200 OK`**: 8-1과 동일한 대회 객체 배열 (`{ items, nextCursor }`, `isBookmarked` 생략 가능)
- **인증**: 필요

## 9. 피드 목록 페이지 (+댓글 모달) [MVP 제외]

**피드 카드 공통 객체** (9-1/9-2/10-2 응답, 검색 결과 재사용):

```json
{
  "feedId": 77,
  "author": {
    "userId": "550e8400-e29b-41d4-a716-446655440015",
    "nickname": "동완러너",
    "profileImageUrl": "...",
    "isDeleted": false
  },
  "content": "오늘도 5km 완주!",
  "images": [
    {
      "feedImageId": 11,
      "url": "https://...",
      "sortOrder": 0
    }
  ],
  "visibility": "PUBLIC",              // FOLLOWERS | PUBLIC | PRIVATE
  "likeCount": 12,
  "commentCount": 3,
  "likedByMe": false,
  "record": {                           // nullable — 러닝기록 템플릿 카드
    "runningRecordId": 501,
    "totalDistanceMeters": 5020,
    "durationSeconds": 1800,
    "averagePaceSecondsPerKm": 359,
    "routePolyline": "u{~vFvyys@fS]pT_@..."   // 다운샘플 경로(encoded polyline) — 카드 지도 미리보기. running_records.route_polyline
  },
  "createdAt": "2026-07-25T11:00:00",
  "updatedAt": "2026-07-25T11:00:00"
}
```

### 9-1. `GET /api/v1/feeds` — 피드 목록 (무한 스크롤)

- **Query**: `tab=FOLLOWING|ALL`(필수), `cursor`/`limit`
- **공개범위 필터**: `FOLLOWING` = 팔로우 유저의 `FOLLOWERS`/`PUBLIC` 피드 + 내 피드 전부, 최신순 / `ALL` = `PUBLIC` 피드 + 팔로우 유저의 `FOLLOWERS` 피드, 최신순 + 가벼운 가중치(개인화 추천은 이후 확장)
- **Response `200 OK`**

```json
{
  "items": [피드 카드],
  "nextCursor": "..."
}
```

- **인증**: 필요

### 9-2. `GET /api/v1/feeds/{feedId}` — 피드 단건

- **화면**: 푸시 랜딩(좋아요/댓글 알림), 검색 결과 탭, 프로필 그리드 탭
- **Response `200 OK`**: 피드 카드

- **에러 (403 Forbidden — 비공개 — `PRIVATE` 타인, `FOLLOWERS` 비팔로워)**

```json
{
  "code": "FEED_NOT_VISIBLE",
  "message": "비공개 피드입니다."
}
```

- **에러 (404 Not Found — 삭제 포함)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 9-3. `POST /api/v1/feeds/{feedId}/like` — 좋아요 / 9-4. `DELETE` — 취소

- **Response `200 OK`** (재조회 방지). idempotent

```json
{
  "likeCount": 13,
  "likedByMe": true
}
```

- 좋아요 시 피드 소유자에게 푸시 (수신 동의 시)

- **에러 (403 Forbidden)**

```json
{
  "code": "FEED_NOT_VISIBLE",
  "message": "비공개 피드입니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 9-5. `GET /api/v1/feeds/{feedId}/comments` — 댓글 목록

- **정렬**: 등록순(오래된 것부터). 답글은 미포함 — `replyCount`만 제공(“답글 N개 보기” 지연 로딩)
- **Response `200 OK`**

```json
{
  "items": [
    {
      "commentId": 201,
      "author": {
        "userId": "550e8400-e29b-41d4-a716-446655440013",
        "nickname": "철수",
        "profileImageUrl": "...",
        "isDeleted": false
      },
      "comment": "고생하셨어요!",
      "likeCount": 2,
      "likedByMe": false,
      "replyCount": 1,
      "isDeleted": false,               // true면 톰스톤(댓글 삭제) — comment=null, "삭제된 댓글입니다" 자리표시. author.isDeleted(작성자 탈퇴)와는 다른 의미
      "createdAt": "2026-07-25T11:05:00"
    }
  ],
  "nextCursor": null
}
```

- **인증**: 필요

### 9-6. `POST /api/v1/feeds/{feedId}/comments` — 댓글/답글 작성

- **Request** — `parentCommentId`는 답글일 때만(선택)

```json
{
  "comment": "...",
  "parentCommentId": 201
}
```

- **depth 1 제한**: `parentCommentId`가 이미 답글인 댓글이면 `400 REPLY_DEPTH_EXCEEDED`
- **Response `201 Created`**: 작성된 댓글 객체 (9-5 형식)
- 피드 소유자(답글이면 원댓글 작성자)에게 푸시 (수신 동의 시)

- **에러 (403 Forbidden)**

```json
{
  "code": "FEED_NOT_VISIBLE",
  "message": "비공개 피드입니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "입력값이 올바르지 않습니다."
}

{
  "code": "REPLY_DEPTH_EXCEEDED",
  "message": "답글에는 답글을 달 수 없습니다."
}
```

- **인증**: 필요

### 9-7. `PATCH /api/v1/comments/{commentId}` — 댓글 수정

- **Request**: `{ "comment": "..." }` (필수, 빈 값 불가)
- **권한**: 댓글 **작성자 본인만** (피드 소유자는 삭제만 가능 — 남의 발언 내용 변경 불가)
- 톰스톤(삭제된 댓글)은 수정 불가. 수정 시 이전 내용을 `delete_comments`에 스냅샷 저장(피드와 동일 — 신고 시 원본 확인용), `updatedAt` 갱신
- **Response `200 OK`**: 수정된 댓글 객체 (9-5 형식)

- **에러 (403 Forbidden — 작성자 아님)**

```json
{
  "code": "FORBIDDEN",
  "message": "권한이 없습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **에러 (409 Conflict — 톰스톤)**

```json
{
  "code": "COMMENT_DELETED",
  "message": "이미 삭제된 댓글입니다."
}
```

- **에러 (400 Bad Request — 빈 내용)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "입력값이 올바르지 않습니다."
}
```

- **인증**: 필요 (작성자)

### 9-8. `GET /api/v1/comments/{commentId}/replies` — 답글 목록 (지연 로딩)

- **Response `200 OK`**: 9-5와 동일 형식(등록순, `replyCount` 없음)
- **인증**: 필요

### 9-9. `DELETE /api/v1/comments/{commentId}` — 댓글 삭제

- **권한**: 댓글 작성자 본인 **또는** 그 댓글이 달린 피드의 소유자
- **동작(레딧 방식)**: 답글 없으면 하드delete, 답글 있으면 톰스톤(내용 비움 + `deletedAt`, 스레드 유지). 두 경우 모두 `delete_comments` 스냅샷 선저장
- **Response**: `204 No Content`

- **에러 (403 Forbidden)**

```json
{
  "code": "FORBIDDEN",
  "message": "권한이 없습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 9-10. `POST /api/v1/comments/{commentId}/like` — 댓글 좋아요 / 9-11. `DELETE` — 취소

- **Response `200 OK`** — idempotent

```json
{
  "likeCount": 3,
  "likedByMe": true
}
```

- **인증**: 필요

## 10. 피드 작성 페이지 (+피드 편집) [MVP 제외]

### 10-1. `POST /api/v1/feeds/images/presigned-url` — 피드 이미지 업로드 URL 발급 (여러 장)

- **Request**

```json
{
  "files": [
    {
      "originalFileName": "a.jpg",
      "mimeType": "image/jpeg"
    }
  ]
}
```

- **Response `200 OK`** — 순서대로 매핑

```json
{
  "items": [
    {
      "feedImageKey": "feeds/2026/07/....jpg",
      "uploadUrl": "https://..."
    }
  ]
}
```

- **인증**: 필요

### 10-2. `POST /api/v1/feeds` — 피드 작성

- **Request**

```json
{
  "content": "오늘도 5km 완주!",          // 선택
  "imageKeys": ["feeds/2026/07/....jpg"],  // 선택 — 업로드 완료된 key, 배열 순서 = sortOrder
  "visibility": "PUBLIC",                  // 필수. FOLLOWERS|PUBLIC|PRIVATE — 기본 선택값 PUBLIC(클라 프리셋)
  "runningRecordId": 501                   // 선택 — 러닝기록 템플릿 (대시보드 진입 시 방금 기록 기본 선택). DB 매핑은 feeds.running_record_id
}
```

- **검증**: `content`/`imageKeys` 둘 다 비면 `400 EMPTY_FEED` (최소 하나 필수)
- **Response `201 Created`**: 피드 카드 — 목록 최상단 노출

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "입력값이 올바르지 않습니다."
}

{
  "code": "EMPTY_FEED",
  "message": "피드 내용이나 이미지를 최소 하나 입력해 주세요."
}
```

- **에러 (404 Not Found — runningRecordId 없음/본인 것 아님)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 10-3. `PATCH /api/v1/feeds/{feedId}` — 피드 수정

- **화면**: 프로필(피드 편집 — 게시글 수정, 노출 범위 설정)
- **Request**: `{ "content"?, "imageKeys"?, "visibility"? }` (부분 수정). 수정 시마다 **이전 내용을 `delete_feeds`에 스냅샷 저장** (신고/차단 등 활용 기능은 **[MVP 제외]**이나 이력은 처음부터 축적)
- **Response `200 OK`**: 수정된 피드 카드

- **에러 (403 Forbidden — 본인 피드 아님)**

```json
{
  "code": "FORBIDDEN",
  "message": "권한이 없습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "EMPTY_FEED",
  "message": "피드 내용이나 이미지를 최소 하나 입력해 주세요."
}
```

- **인증**: 필요 (소유자)

### 10-4. `DELETE /api/v1/feeds/{feedId}` — 피드 삭제

- **동작**: `deletedAt` 소프트delete — 전체 조회에서 제외
- **Response**: `204 No Content`

- **에러 (403 Forbidden — 본인 피드 아님)**

```json
{
  "code": "FORBIDDEN",
  "message": "권한이 없습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요 (소유자)

## 11. 프로필 페이지

### 11-1. `GET /api/v1/users/me` — 내 기본 정보

- **화면**: 전역 (내 프로필 진입, 편집 프리필 등)
- **Response `200 OK`**: `{ "userId", "email", "nickname", "profileImageUrl", "introduction", "isOnboarded" }` — `email`은 항상 존재(소셜 포함 모든 유저 이메일 보유, 미동의 시 가입 자체가 `403`으로 거부)
- **인증**: 필요

### 11-2. `GET /api/v1/users/{userId}` — 프로필 요약

- **화면**: 프로필 (본인/타인 공통 — 본인이면 편집·설정 버튼, 타인이면 팔로우 버튼 노출은 `isMe`로 분기)
- **Response `200 OK`**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440015",
  "isMe": false,
  "nickname": "동완러너",
  "profileImageUrl": "https://...",
  "introduction": "즐겁게 달려요",
  "followerCount": 42,
  "followingCount": 38,
  "isFollowing": true,
  "isMutual": true,
  "elevationGainTotalMeters": 3200,    // 평생 누적 상승 고도 (running_records.elevation_gain 전체 합산, null 제외) — 세션 결과의 totalElevationGainMeters(1회 러닝 합)와 다른 값
  "mileageTotalMeters": 320500,        // 누적 마일리지 (`running_records` 합산)
  "mileageMonthlyMeters": 42200        // 이번 달 마일리지
}
```

- **지인 마스킹**: `profile_visibility=FRIENDS`인 사용자를 친구가 아닌 사람이 조회하면 컬렉션·친구 목록 조회가 `403 PROFILE_PRIVATE`. 사진·닉네임·소개글·마일리지·최고 페이스·러닝 횟수·친구 수는 항상 공개

- **에러 (404 Not Found — 탈퇴 포함)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 11-3. `GET /api/v1/users/{userId}/feeds` — 피드 그리드 (경량)

- **Response `200 OK`** — 탭하면 9-2 단건 조회로 상세

```json
{
  "items": [
    {
      "feedId": 77,
      "thumbnailUrl": "https://...",
      "imageCount": 3
    }
  ],
  "nextCursor": "..."
}
```

- **공개범위**: 본인 = 전부(`PRIVATE` 포함) / 타인 = `PUBLIC` (+팔로워면 `FOLLOWERS`)
- **인증**: 필요

### 11-6. `POST /api/v1/users/{userId}/follow` — 팔로우 / 11-7. `DELETE` — 언팔로우

- **Response `200 OK`** (대상의 갱신 카운트 — `user_follow_stats`). idempotent

```json
{
  "isFollowing": true,
  "isMutual": false,
  "followerCount": 43
}
```

- 팔로우 시 대상에게 "새 팔로워" 푸시 (수신 동의 시)

- **에러 (400 Bad Request)**

```json
{
  "code": "CANNOT_FOLLOW_SELF",
  "message": "자기 자신은 팔로우할 수 없습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 11-8. `GET /api/v1/users/{userId}/followers` / 11-9. `.../followings` — 팔로워·팔로잉 목록

- **화면**: 팔로워/팔로잉 목록 페이지
- **Query**: `q`(이름 필터), `cursor`/`limit`
- **Response `200 OK`**: `{ "items": [ { "userId", "nickname", "profileImageUrl", "isFollowing", "isMutual" } ], "nextCursor": "..." }`

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 11-10. `GET /api/v1/users/search` — 사용자 검색

- **화면**: 러너 검색 — **친구를 추가하려면 먼저 사람을 찾아야 하므로 친구 기능의 진입점이다**
- **Query**: `q`(필수, 닉네임), `cursor`/`limit`
- **Response `200 OK`**: `{ "items": [ { "userId", "nickname", "profileImageUrl" } ], "nextCursor": "..." }`
- 검색 결과에 친구 관계 상태를 함께 내릴지는 친구 API를 정리할 때 확정한다.
- **인증**: 필요

## 12. 프로필 편집 페이지

### 12-1. `POST /api/v1/users/me/profile-image/presigned-url` — 프로필 사진 업로드 URL

- **Request**

```json
{
  "originalFileName": "me.jpg",
  "mimeType": "image/jpeg"
}
```

- **Response `200 OK`**

```json
{
  "profileImageKey": "profiles/....jpg",
  "uploadUrl": "https://..."
}
```

- **인증**: 필요

### 12-2. `PATCH /api/v1/users/me` — 프로필 수정

- **Request**: `{ "nickname"?, "introduction"?, "profileImageKey"? }` (부분 수정) — `nickname`은 서버가 `user_onboardings.nickname` 갱신(서비스 전반 표시 갱신). 키·몸무게 수정은 2차 예정, 평균 페이스는 수정 불가(서버 자동 갱신)
- **Response `200 OK`**: 11-1 형태 갱신본

- **에러 (409 Conflict)**

```json
{
  "code": "NICKNAME_ALREADY_EXISTS",
  "message": "이미 사용 중인 닉네임입니다."
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "입력값이 올바르지 않습니다."
}
```

- **인증**: 필요

## 13. 설정 페이지

### 13-1. `GET /api/v1/users/me/settings` — 설정 조회

```json
{
  "alertConsent": true                   // 전체 알림 on/off (단일 토글, 기본 on)
}
```

- **`alertConsent` = 단일 토글** — 매칭 확정/실패, 세션 시작 리마인더, 친구 요청 도착/수락을 한 번에 on/off (`users.alert_consent`). **기본값 `true`** — 넷 다 거래성 알림이라 수신 동의 대상이 아니고, 가입 직후부터 매칭 확정 푸시가 도달해야 한다. OS 알림 권한과는 별개로 동작한다(둘 중 하나라도 꺼져 있으면 미도달)
- **공개범위 설정**: `profileVisibility`(FRIENDS/PUBLIC — 지인 마스킹 on/off). `feedDefaultVisibility`(피드 작성 기본값)는 **[MVP 제외]** — 피드 기본값은 클라 PUBLIC 프리셋
- **인증**: 필요

### 13-2. `PATCH /api/v1/users/me/settings` — 설정 변경

- **Request**: 13-1 필드 부분 수정 / **Response `200 OK`**: 갱신본
- **인증**: 필요

### 13-3. `DELETE /api/v1/users/me` — 회원탈퇴

- **화면**: 설정 (확인 팝업 후)
- **동작 (테이블별 정책)**: `delete_users` 스냅샷(email/alertConsent/createdAt) → `users` 하드delete. **유지**: `feeds`/`comments`/`running_records`(+splits)/좋아요(카운트 유지) — 작성자는 "탈퇴한 사용자" 고정 표시. **CASCADE 삭제**: `follows` + 상대방 `user_follow_stats` 탈퇴 트랜잭션 내 즉시 재계산. **삭제**: `user_onboardings`/`user_devices`/`oauth_users`/`user_running_contests`/`running_players`(연결 `running_room_sessions` 연쇄) + 본인 `user_follow_stats`
- **Response**: `204 No Content` (토큰 즉시 무효화)
- **인증**: 필요
