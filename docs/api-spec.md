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
- 매칭 시작·취소: REST (아래 5번). 대기 현황은 SSE 스트림으로 수신

### 4. 매칭완료 대기방

- 대기방 정보·참가자 목록: 매칭 SSE 스트림 (아래 5번)
- 나가기: `DELETE /api/v1/users/me/running-match`
- 친구 초대: 엔드포인트 미정 — 구현 후순위 (상세 4번)

### 5. 매칭·러닝 실시간 통신

매칭은 **REST + SSE**, 러닝 구간은 **WebSocket**이다. 전환 절차는 상세 5번 머리말 참고.

**매칭 REST**

| # | Method | Path | 설명 |
|---|--------|------|------|
| 11 | POST | `/api/v1/running-matches` | 매칭 신청 (시각+거리) — 409 `ALREADY_MATCHING` |
| 12 | DELETE | `/api/v1/users/me/running-match` | 대기 취소 + 확정 후 나가기 겸용 (서버가 방 상태로 분기) |
| 13 | GET | `/api/v1/users/me/running-match` | 현재 매칭 상태 — 홈 진입·앱 재시작 시 파생 상태 조회 |
| 14 | GET | `/api/v1/running-matches/slots` | 시간대별 대기 인원 — 매칭 입력 모달의 "3명 대기 중" 표시 |
| 15 | GET | `/api/v1/running-matches/stream` | 매칭 이벤트 스트림 (SSE) |
| 16 | POST | `/api/v1/running-rooms` | 솔로 러닝 개시 (매칭 방은 서버가 생성) |

**매칭 SSE** — 이벤트 3종. 연결 직후 현재 상태 스냅샷을 받는다.

| 이벤트 | 비고 |
|--------|------|
| `MATCH_PLAYERS_UPDATED` | 대기 인원 변동 (방은 신청 즉시 생기므로 "배정 전" 상태가 없다) |
| `MATCH_STARTED` | 매칭 성사 통지 (`RoomInfo`) |
| `MATCH_ROOM_UPDATED` | `RoomInfo`에 `status` 포함 — 취소 통지도 `status: CANCELLED`로 처리 |

**러닝 WebSocket** — `/ws/running-rooms`, 메시지 8종. 매칭 러닝과 솔로 러닝이 같은 채널을 쓴다. 이 외에 **ack 2종**(`RUNNING_STARTED`·`RUNNING_FINISHED`)이 있다.

| 그룹 | 메시지 | 방향 | 비고 |
|------|--------|------|------|
| 카운트 다운 | `RUNNING_START` | C→S | 클라 주도 시작 |
| 러닝 중 | `RUNNING_LOCATION_UPDATE` | C→S | 고빈도 — ack 없음 |
| 러닝 중 | `PLAYER_RUNNING_PROGRESS_UPDATED` | S→C | `paused` 포함 — 멈춘 것과 느려진 것을 구분 |
| 러닝 중 | `RUNNING_PAUSE` / `RUNNING_RESUME` | C→S | 일시정지·재개 — 본인 기록만 멈춘다 |
| 러닝 중 | `COACHING_EVENT` | S→C | 음성 코칭 — 서버는 판정·발신만, 재생은 클라 |
| 러닝 중 | `RUNNING_FINISH` | C→S | `forced` 플래그로 강제 종료 포함 — 이 시점에 서버가 `running_records` 저장 |
| 공통 | `ERROR` | S→C | WS 요청 실패 통지 |

### 6. 러닝 중 / 러닝 후 대시보드

| # | Method | Path | 설명 |
|---|--------|------|------|
| 17 | GET | `/api/v1/running-rooms/{runningRoomId}/results` | 참가자 전원 최종 결과 — 사용 화면: 러닝 후 대시보드 |
| 18 | GET | `/api/v1/running-rooms/{runningRoomId}/split-results` | 구간별 상세 + GPS 경로 |

### 7. 기록 화면

| # | Method | Path | 설명 |
|---|--------|------|------|
| 19 | GET | `/api/v1/users/me/running-records` | 내 러닝 기록 목록(기간 필터, 캘린더용) — 사용 화면: 기록, 피드 작성(템플릿 선택) |
| 20 | GET | `/api/v1/running-records/{runningRecordId}` | 기록 상세 (경로·구간 포함) |

### 8. 대회 화면 [MVP 제외]

| # | Method | Path | 설명 |
|---|--------|------|------|
| 21 | GET | `/api/v1/contests` | 대회 목록 + 검색·필터(날짜/지역/거리). 상세 API 없음 — 목록에 `detailUrl` 포함(공식 홈페이지 이동) |
| 22 | POST | `/api/v1/contests/{contestId}/bookmark` | 일정 추가(북마크) |
| 23 | DELETE | `/api/v1/contests/{contestId}/bookmark` | 북마크 해제 |
| 24 | GET | `/api/v1/users/me/contest-bookmarks` | 북마크한 대회 목록 — 사용 화면: 기록(캘린더 병합), 대회 |

### 9. 피드 목록 페이지 (+댓글 모달) [MVP 제외]

| # | Method | Path | 설명 |
|---|--------|------|------|
| 25 | GET | `/api/v1/feeds` | 피드 목록, `tab=FRIENDS\|ALL`, 무한 스크롤 |
| 26 | GET | `/api/v1/feeds/{feedId}` | 피드 단건 — 사용 화면: 푸시 랜딩, 검색 결과, 프로필 그리드 탭 |
| 27 | POST | `/api/v1/feeds/{feedId}/like` | 좋아요 (응답에 갱신 카운트) |
| 28 | DELETE | `/api/v1/feeds/{feedId}/like` | 좋아요 취소 |
| 29 | GET | `/api/v1/feeds/{feedId}/comments` | 댓글 목록 (등록순, 답글 제외) |
| 30 | POST | `/api/v1/feeds/{feedId}/comments` | 댓글/답글 작성 (`parentCommentId` 옵션, depth 1 제한) |
| 31 | PATCH | `/api/v1/comments/{commentId}` | 댓글 수정 (작성자 본인만) |
| 32 | GET | `/api/v1/comments/{commentId}/replies` | 답글 지연 로딩 ("답글 N개 보기") |
| 33 | DELETE | `/api/v1/comments/{commentId}` | 댓글 삭제 (작성자 or 피드 소유자, 레딧 방식) |
| 34 | POST | `/api/v1/comments/{commentId}/like` | 댓글 좋아요 |
| 35 | DELETE | `/api/v1/comments/{commentId}/like` | 댓글 좋아요 취소 |

### 10. 피드 작성 페이지 (+프로필의 피드 편집) [MVP 제외]

| # | Method | Path | 설명 |
|---|--------|------|------|
| 36 | POST | `/api/v1/feeds/images/presigned-url` | 피드 이미지 업로드 URL 발급 (여러 장) |
| 37 | POST | `/api/v1/feeds` | 피드 작성 (텍스트/이미지 최소 1, 공개범위, 기록 템플릿 `runningRecordId`) |
| 38 | PATCH | `/api/v1/feeds/{feedId}` | 피드 수정 (내용·공개범위) — 사용 화면: 프로필(피드 편집) |
| 39 | DELETE | `/api/v1/feeds/{feedId}` | 피드 삭제 (소프트delete) |

### 11. 프로필 페이지 (본인/타인)

| # | Method | Path | 설명 |
|---|--------|------|------|
| 40 | GET | `/api/v1/users/me` | 내 기본 정보 — 사용 화면: 전역 |
| 41 | GET | `/api/v1/users/{userId}` | 프로필 요약 (마일리지·최고 페이스·러닝 횟수·친구 수) |
| 42 | GET | `/api/v1/users/{userId}/feeds` | 피드 그리드 (경량: 썸네일+장수) |
| 43 | POST | `/api/v1/users/{userId}/friend-request` | 친구 요청 — 사용 화면: 프로필, 러너 검색 |
| 44 | DELETE | `/api/v1/users/{userId}/friend-request` | 요청 취소(보낸 쪽) · 거절(받은 쪽) |
| 45 | POST | `/api/v1/users/{userId}/friend` | 친구 요청 수락 |
| 46 | DELETE | `/api/v1/users/{userId}/friend` | 친구 삭제 |
| 47 | GET | `/api/v1/users/me/friends` | 내 친구 목록 (+이름 검색) |
| 48 | GET | `/api/v1/users/me/friend-requests` | 받은 친구 요청 목록 |
| 49 | GET | `/api/v1/users/{userId}/colors` | 컬러 컬렉션 (마스터 전체 + 획득 여부) |
| 50 | GET | `/api/v1/users/search` | 사용자 검색 — 친구 추가 진입점 (`?q=검색어`) |

### 12. 프로필 편집 페이지

| # | Method | Path | 설명 |
|---|--------|------|------|
| 51 | POST | `/api/v1/users/{userId}/profile-image/presigned-url` | 프로필 사진 업로드 URL 발급 |
| 52 | PATCH | `/api/v1/users/{userId}/profile-image` | 업로드한 사진 반영 — S3 존재·소유자 검증 |
| 53 | GET | `/api/v1/users/{userId}/profile-image` | 프로필 사진 URL 조회 — 인증 불필요 |
| 54 | DELETE | `/api/v1/users/{userId}/profile-image` | 프로필 사진 삭제 — S3 객체는 남기고 키 연결만 끊음 |
| 55 | PATCH | `/api/v1/users/me` | 인사말 변경 |
| 56 | PATCH | `/api/v1/users/{userId}/nickname` | 닉네임 변경 (중복 시 409) |
| 57 | POST | `/api/v1/users/nickname/availability` | 닉네임 중복 확인 — 사용 화면: 프로필 편집, 온보딩 |

### 13. 설정 페이지

| # | Method | Path | 설명 |
|---|--------|------|------|
| 58 | GET | `/api/v1/users/me/account` | 계정 정보 — 이메일 + 로그인 수단(비밀번호 변경 노출 판정) |
| 59 | PATCH | `/api/v1/users/{userId}/password` | 비밀번호 변경 (로컬 계정만, 본인만) |
| 60 | GET | `/api/v1/users/me/settings` | 알림 on/off(단일) + 프로필 공개범위 조회 |
| 61 | PATCH | `/api/v1/users/me/settings` | 설정 변경 |
| 62 | DELETE | `/api/v1/users/me` | 회원탈퇴 (스냅샷→하드delete, 테이블별 정책) |

**합계: REST 62개 + SSE 스트림 1개(이벤트 3종) + WebSocket 채널 1개(메시지 8종)**

---

# 상세 명세

## 0. 공통 규칙

- **인증**: `Authorization: Bearer {accessToken}` 헤더. Access+Refresh 토큰 이원화, **refresh rotation** — 재발급 시 accessToken·refreshToken 모두 교체(이전 refreshToken 무효). refreshToken은 **바디 전달 + 클라 Keychain/Keystore 보관**. **로그아웃 시 해당 access 토큰은 서버 차단(블랙리스트)**
- **페이지네이션 limit**: `?limit=` 생략 시 기본 **20**, 최대 **50**(초과 요청은 50으로 클램프)
- **시각**: 시점은 ISO 8601 **`yyyy-MM-ddTHH:mm:ss`**(예: `2026-07-20T13:00:00`) — **KST 기준, 타임존 오프셋 없이 초 단위까지**. 클라이언트는 이 값을 KST로 해석한다. 달력 날짜(생일·대회 일정)는 `YYYY-MM-DD`
- **단위**: **거리는 전부 미터, 페이스는 초/km 정수**(`390` → "6:30") — 표시 변환은 프론트 몫(DB에 km로 저장된 값도 API에선 미터)
- **토글 액션**: POST(등록)/DELETE(취소) 분리, idempotent(중복 호출 시 에러 없이 성공 응답) — 좋아요는 갱신 상태·카운트 포함 `200 OK`, 대회 북마크는 `204 No Content`. **친구는 토글이 아니다** — 요청·수락·삭제가 각각 다른 동작이라 11-6~11-8로 나뉜다
- **enum**: DB·API **동일한 영문 코드**(변환 매핑 없음) — 값 목록은 `erd.md` §7(enum 사전)
- **이미지 업로드 공통(Presigned)**: ① 업로드 URL 발급 API → ② 클라가 S3에 직접 업로드 → ③ 반환받은 `key`(또는 완료 API)를 본 API에 전달
- **탈퇴 유저 작성자 표시**: `{ "userId": "550e8400-...", "nickname": "탈퇴한 사용자", "profileImageUrl": null, "isDeleted": true }` (고정 문구, `userId`는 UUID 문자열 유지)
- **`[MVP 제외]` 표기**: 지금 만들지 않는 엔드포인트. 정의는 그대로 두어 확장 시점에 재작성 없이 쓴다. 마커가 없으면 만드는 것이며, 차수(1차·2차)는 적지 않는다.
- **ID 타입 규칙**: `userId` = **UUID 문자열** (ERD `users.user_id`가 UUID). 그 외 리소스 ID(`runningRoomId`, `feedId`, `commentId`, `contestId`, `runningRecordId`, `colorId` 등) = **Long**

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
  "refreshToken": "ey..."
}
```

- **`isOnboarded`는 내리지 않는다** — 온보딩 완료 여부는 `GET /users/me`로 판정한다. 인증 응답 셋(회원가입·로그인·소셜 로그인)이 모두 같다

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
  "refreshToken": "ey..."
}
```

- 닉네임 등 상세와 **온보딩 완료 여부(`isOnboarded`)는 `GET /users/me`** 로 확인한다

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

> **서버 매핑은 `POST /auth/oauth/{provider}` 하나다.** 위 두 경로는 클라이언트가 실제로 호출하는 구체 URL이며, provider별 핸들러를 따로 두지 않는다. `{provider}`는 `google`·`kakao`(대소문자 무시). 지원하지 않는 값은 404가 아니라 **400 `UNSUPPORTED_PROVIDER`**로 응답한다 — 경로 자체는 매칭되기 때문이다.

- **Request** (둘 다 필수, 구글·카카오 공통)

```json
{
  "authorizationCode": "...",
  "codeVerifier": "..."
}
```

- **동작**: 서버가 provider에 인가 코드 교환(PKCE `codeVerifier` 검증) → 유저 정보 조회 → `provider_id`로 `oauth_users` 조회, 없으면 생성(회원가입) → 자체 토큰 발급
- **Response `200 OK`**: 1-4 로그인과 동일 형태 (`userId`/`accessToken`/`refreshToken`) — 최초 가입 여부와 무관하게 토큰 발급. `isOnboarded`는 담지 않으며 `GET /users/me`로 판정한다
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

- **약관 동의**: 별도 요청 필드 없음 — **가입 흐름 첫 화면**에서 받고 가입 자체를 동의로 갈음한다. 동의 시각 증빙 = `users.created_at`. 소셜은 신규 여부를 미리 알 수 없어 화면을 끼우지 않고 버튼 아래 문구로 갈음한다

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

초대받은 사람은 `running_players.status='INVITED'`로 생성되고, 수락하면 `JOINED`, 거절하면 row를 DELETE한다(거절 이력 보관 안 함).

초대방은 `running_rooms.type='INVITE'`로 만든다 — 랜덤 매칭 후보 스캔(`type='MATCH'`)에 잡히면 남의 초대방에 모르는 사람이 배정되므로 값 분리가 필수다.

**`INVITED`는 활성 신청이 아니다.** 초대를 여러 개 동시에 받을 수 있고, 초대를 받은 채로 랜덤 매칭도 신청할 수 있다(근거는 `feature-spec.md`). 활성 판정은 `status='JOINED'`인 행으로만 하므로, 제약은 전부 **수락 시점 한 곳**에 모인다.

- 이미 `JOINED` 행이 있으면 **`409 ALREADY_MATCHING`** — 클라는 기존 매칭 취소를 안내한 뒤 다시 수락시킨다
- 방이 정원까지 찼으면 409 — 먼저 수락한 사람에게 자리가 갔다
- 방이 이미 마감(`MATCHED`)이거나 취소(`CANCELLED`)면 거부
- 수락에 성공하면 **그 유저의 남은 `INVITED` row를 전부 DELETE한다** — 하나를 고른 이상 나머지 초대는 소멸한다
- 방이 마감·취소로 넘어갈 때 응답 없이 남은 `INVITED` row도 함께 정리한다 — 두지 않으면 죽은 row가 쌓인다

`INVITED` 상태에서는 SSE를 연결하지 않는다(활성 신청이 아니므로 — 15번 참조). 초대 도착은 푸시로 알리고, 수락한 뒤에 스트림을 연다. 초대에 응답하지 않아도 이탈로 보지 않는다 — `status`가 `LEFT`가 되지 않기 때문이다.

## 5. 매칭·러닝 실시간 통신

**구간마다 통신 방식이 다르다.**

| 구간 | 방식 | 이유 |
|---|---|---|
| 매칭 신청 ~ 대기방 (5-A·5-B) | **REST + SSE** `/api/v1/running-matches/stream` | 클라가 보내는 건 신청·취소 둘뿐이고 나머지는 전부 서버 푸시다 — 양방향 채널을 쓸 이유가 없다 |
| 러닝 구간 (5-C·5-D) | **WebSocket** `/ws/running-rooms` | 위치를 주기 발신하는 고빈도 양방향 구간 |

**솔로 러닝도 같은 WebSocket을 쓴다.** 매칭을 거치지 않을 뿐 좌표 수집·저장 경로는 동일하다. 시작할 때 `POST /running-rooms`로 방을 만들어 `runningRoomId`를 받은 뒤 WS에 연결한다(5-C의 카운트다운은 건너뛴다 — 맞출 상대가 없다).

#### `POST /api/v1/running-rooms` — 솔로 러닝 개시

- **클라가 만들 수 있는 방은 솔로뿐이다.** 매칭 방은 신청 시 서버가 만들므로 요청 대상이 아니다
- **Request**

```json
{
  "targetDistanceMeters": 5000
}
```

- **Response `201 Created`**

```json
{
  "runningRoomId": 126
}
```

- **동작**: `running_rooms` 행을 `type='SOLO'`, `max_member=1`, `current_member=1`로 만들고 바로 `status='STARTED'`로 둔다(매칭을 거치지 않으므로 `MATCHING` 단계가 없다). 본인 `running_players` 1행도 함께 만든다 — 참가자 없는 방을 남기지 않는다
- 이 방은 `GET /running-matches/slots`의 대기 인원 집계에 포함되지 않는다(`type='SOLO'`로 제외). 모집 중인 자리가 아니다
- **에러 (409 Conflict)**: `ALREADY_MATCHING` — 진행 중인 러닝이나 활성 매칭 신청이 있다
- **인증**: 필요

**전환 지점** — `scheduledStartAt` 도달 시:

1. 클라가 WS `/ws/running-rooms` 연결
2. `RUNNING_START` 발신 → `RUNNING_STARTED` ack 수신
3. **ack를 받은 뒤에** SSE 스트림을 닫는다

ack 전에 SSE를 닫지 않는다 — WS 연결이 실패하면 돌아갈 채널이 없어진다.

- **DB row 트리거** — `running_players`가 `running_room_id`로 방을 직접 가리킨다(신청 즉시 방이 생기므로 항상 값이 있다)
  - row 생성 = 매칭 신청·솔로 개시 시. 새 방을 만들거나 기존 모집 중인 방에 배정된다
  - 취소·나가기 요청 시 서버가 방 상태로 분기 — 대기 중(`MATCHING`)이면 `running_players` row DELETE(마지막 참가자였으면 방도 `CANCELLED`), 확정 후(`MATCHED`)면 **row 유지 + `status=LEFT`**(어느 방에서 나갔는지가 이력 근거)
  - 방 자동 취소 시 전원 유지. 원칙: "확정 전엔 지우고, 확정 후엔 남긴다"

### 5-A. 매칭 중 (홈 → 매칭 대기 화면)

#### `GET /api/v1/running-matches/stream` — 매칭 이벤트 스트림 (SSE)

- **인증**: 필요 / **Content-Type**: `text/event-stream`
- **연결 시점**: 매칭 신청 성공 직후. 활성 신청이 없으면 연결하지 않는다 — 서버가 보낼 것이 없다.
  - 앱 재시작·포그라운드 복귀 시엔 `GET /users/me/running-match`로 활성 여부를 확인하고 있으면 재연결한다.
- **종료 시점**: 러닝 시작(위 전환 절차), 매칭 취소, 매칭 실패 — 서버가 스트림을 닫는다.
- **연결을 화면 생명주기에 묶지 않는다.** 매칭 대기 중 현황 배너가 모든 화면에서 유지돼야 하므로, 홈을 벗어나도 스트림은 살아 있어야 한다.
- **이벤트 형식** — 타입은 SSE `event` 필드로, 본문은 `data`에 JSON으로 싣는다

```
event: MATCH_ROOM_UPDATED
data: {"runningRoomId":125,"status":"MATCHED", ...}
```

| 이벤트 | 시점 |
|---|---|
| `MATCH_PLAYERS_UPDATED` | 대기 인원 변동 (방 배정 전후 모두) |
| `MATCH_STARTED` | 매칭 확정 — `data` = `RoomInfo` |
| `MATCH_ROOM_UPDATED` | 확정된 방의 정보 변동 — `data` = `RoomInfo` |

- **연결 직후 서버가 현재 상태를 한 번 보낸다.** 매칭 이벤트는 전부 **전체 상태를 담으므로** 놓친 이벤트를 되짚을 필요가 없다 — `Last-Event-ID` 재개를 쓰지 않는 이유다.
- **keep-alive**: 주기적으로 주석 라인(`: ping`)을 보내 프록시 유휴 타임아웃을 막는다. 주기는 운영값.
- 스트림은 수신 전용이라 요청 실패라는 개념이 없다 — 오류는 신청·취소 REST 응답으로 전달된다.

#### `GET /api/v1/running-matches/slots` — 시간대별 대기 인원

- **화면**: 매칭 정보 입력 모달 — 시간 선택 박스에 "19:00 · 3명 대기 중"처럼 표시한다
- **Query**: `date`(YYYY-MM-DD, 생략 시 오늘), `targetDistanceMeters`(선택 — 주면 해당 거리 조건만 집계)
- **Response `200 OK`**

```json
{
  "slots": [
    { "scheduledStartAt": "2026-07-25T18:00:00", "waitingCount": 0, "selectable": false },
    { "scheduledStartAt": "2026-07-25T18:30:00", "waitingCount": 3, "selectable": true },
    { "scheduledStartAt": "2026-07-25T19:00:00", "waitingCount": 1, "selectable": true }
  ]
}
```

- `waitingCount`는 아직 확정되지 않은 대기자 수다 — `type='MATCH' AND status='MATCHING'`인 방의 참가자만 센다. 이미 `MATCHED`된 방(들어갈 수 없는 자리)과 솔로 방(`type='SOLO'`)은 제외
- `selectable=false`는 마감이 지난 슬롯 — 목록에는 남기되 선택은 막는다
- **인증**: 필요

#### `POST /api/v1/running-matches` — 매칭 신청

- **화면**: 홈 (매칭 버튼)
- **Request**

```json
{
  "scheduledStartAt": "2026-07-25T19:00:00",
  "targetDistanceMeters": 5000
}
```

- **입력값은 정해진 선택지 안에서만 받는다** — 자유 입력이 아니다

| 필드 | 허용값 |
|---|---|
| `scheduledStartAt` | **18:00~22:00**, **30분 간격** (`18:00`, `18:30`, … `22:00`) |
| `targetDistanceMeters` | **3000 / 5000 / 10000** 셋 중 하나 |

- 조건을 좁게 고정하는 이유는 매칭 성사율이다. 자유 입력이면 같은 조건에 두 명이 모일 확률이 급격히 떨어진다
- **활성 신청은 1개** — 이미 있으면 `409 ALREADY_MATCHING`
- 모든 방은 공개 랜덤 매칭 — 프라이빗 방 없음
- 페이스 조건은 입력받지 않음 — 서버가 보관한 사용자 평균 페이스 자동 사용 (온보딩 입력값에서 시작, 이후 러닝 기록 기반 자동 갱신)
- **모집 인원도 입력받지 않음** — 서버가 2~4명 범위에서 자동 편성 (`desiredMemberCount` 필드 없음)
- **Response `201 Created`** — 신청이 접수되면 `running_players` row가 생기고, 같은 조건에 모집 중인 방이 있으면 거기 배정되고 없으면 **1인 방**(`running_rooms`, `type='MATCH'`, `status='MATCHING'`, `max_member=4`, `current_member=1`)이 새로 생긴다
  - **응답 본문에 `runningRoomId`를 넣지 않는다.** 방은 있지만 매칭 단계의 클라는 방 ID로 호출할 곳이 없다 — 필요한 시점(참가자·방 갱신)에 SSE로 내려간다

```json
{
  "scheduledStartAt": "2026-07-25T10:00:00",
  "targetDistanceMeters": 5000,
  "closeAt": "2026-07-25T09:45:00"
}
```

- `closeAt`은 모집이 마감되는 시각(`running_rooms.close_at`) — 대기 배너의 "마감까지 남은 시간" 표시에 쓴다. 이 시각이 지나면 새 참가자가 들어올 수 없고 확정 판정이 돈다
- **응답을 받은 뒤 SSE 스트림에 연결한다**
- **에러 (409 Conflict)**: `ALREADY_MATCHING` — 이미 활성 신청이나 확정된 방이 있다
- **에러 (409 Conflict)**: `MATCH_COOLDOWN` — 확정된 매칭에서 이탈해 신청이 제한된 상태다. 응답에 해제 시각을 담는다

```json
{
  "code": "MATCH_COOLDOWN",
  "message": "확정된 매칭에서 이탈해 일정 시간 신청이 제한됩니다.",
  "cooldownUntil": "2026-07-26T07:30:00"
}
```

- 솔로 러닝(`POST /running-rooms`)은 이 제한을 받지 않는다
- **인증**: 필요

#### `DELETE /api/v1/users/me/running-match` — 매칭 취소·방 나가기 (겸용)

- **서버가 방 상태로 분기**
  - 대기 중(`MATCHING`) = 대기 취소(`running_players` row 삭제). **본인이 마지막 참가자였으면 방도 `CANCELLED`** — 참가자 없는 방을 모집 대상으로 남기지 않는다. 제재 없음
  - 확정 후(`MATCHED`) = 이탈(`status=LEFT`, `left_at` 기록, row 유지). **`close_at` + 유예를 지난 뒤면 매칭 신청 쿨다운이 걸린다** — 클라는 나가기 전에 그 사실을 안내한다
  - 남은 인원에게는 `MATCH_PLAYERS_UPDATED` 또는 `MATCH_ROOM_UPDATED`를 스트림으로 발신한다. **혼자 남아도 방은 취소하지 않는다** — 남은 사람은 그대로 러닝을 진행한다
- **시각으로 취소를 차단하지 않는다.** 시작 직전까지 호출할 수 있고, 늦게 나가는 것은 차단이 아니라 쿨다운으로 다룬다 — 막아도 앱 강제 종료로 우회되며 그 경우 `LEFT`조차 남지 않는다
- **Response `204 No Content`** — 이후 클라는 SSE 스트림을 닫는다
- **에러 (404 Not Found)**: 활성 신청이 없다
- **인증**: 필요

#### `GET /api/v1/users/me/running-match` — 현재 매칭 상태 조회

- **화면**: 홈 진입·앱 재시작 — 스트림에 연결할지 판단하고 홈 상태를 그린다
- 스트림도 연결 직후 같은 정보를 보내지만 이 API를 따로 둔다. **매칭을 걸지 않은 사용자가 대다수인데 전원에게 스트림을 여는 것은 서버 커넥션과 단말 배터리 양쪽에 부담**이라, 활성 신청이 있는지 먼저 확인하고 있을 때만 연결한다
- **Response `200 OK`** — 활성 신청이 없으면 `{ "state": "NONE" }`

```json
{
  "state": "MATCHED",
  "runningRoomId": 125,
  "room": { ... }
}
```

- **`state`는 저장값이 아니라 파생값이다** — `running_players`와 방 상태·마감 시각으로 계산한다. `feature-spec.md`의 홈 화면 상태 표와 **같은 규칙**이며 이름만 한글/영문으로 다르다

| `state` | 조건 |
|---|---|
| `NONE` | 활성 `running_players` 없음 |
| `WAITING` | 방이 `MATCHING`이고 마감 전 |
| `MATCHED` | 방이 `MATCHED` |
| `FAILED` | 방이 `CANCELLED`, 또는 마감이 지났는데 아직 `MATCHING`(스케줄러가 아직 닫지 않은 구간) |

- `room`은 `state`가 `MATCHED`일 때만 채워지며 `RoomInfo`와 같은 구조다
- **인증**: 필요

#### `MATCH_PLAYERS_UPDATED` (SSE) — 매칭 참여자 갱신

```json
{
  "runningRoomId": 125,
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

- `runningRoomId`는 **항상 값이 있다** — 신청 즉시 1인 방이 생기므로 매칭 대기 중에도 가리킬 방이 존재한다. 다만 이 값이 "매칭이 확정됐다"는 뜻은 아니다. 확정 여부는 `MATCH_ROOM_UPDATED`의 `status`와 `GET /users/me/running-match`의 `state`로만 판정한다
- 매칭 무산(마감 시점 2명 미만)·방 취소 통지: 별도 이벤트 없음 — **`MATCH_ROOM_UPDATED`의 `status: "CANCELLED"`**로 전달. 수신 시 클라는 홈으로

### 5-B. 매칭 방 (매칭완료 대기방)

#### 공통 객체 `RoomInfo` — 매칭방 전체 정보

`MATCH_STARTED`와 `MATCH_ROOM_UPDATED`의 `data`는 아래 **동일 구조를 공유** (정의 한 곳 — 서버도 같은 직렬화 재사용):

```json
{
  "runningRoomId": 125,
  "status": "MATCHED",               // running_rooms.status: MATCHING|MATCHED|STARTED|FINISHED|CANCELLED — CANCELLED면 클라는 홈으로
  "scheduledStartAt": "2026-07-25T10:00:00",
  "targetDistanceMeters": 5000,
  "teamAveragePaceSecondsPerKm": 375,
  "players": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440015",
      "nickname": "동완러너",
      "status": "JOINED",              // PlayerStatus: INVITED | JOINED | LEFT
      "profileImageUrl": "https://...",
      "introduction": "즐겁게 같이 달려요!",   // users.introduction
      "averagePaceSecondsPerKm": 360
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440013",
      "nickname": "철수",
      "status": "JOINED",
      "profileImageUrl": "https://...",
      "introduction": "천천히 오래 달려요.",
      "averagePaceSecondsPerKm": 390
    }
  ]
}
```

#### `MATCH_STARTED` (SSE) — 매칭 성사 통지

- `data` = `RoomInfo`. 수신 시 클라는 대기 화면 → 매칭방 화면으로 전환
- **발화 시점은 모집 마감(`close_at`)이다** — 방이 `MATCHING`→`MATCHED`로 넘어가는 순간 한 번. **방 생성 시점이 아니다.** 방은 신청 즉시 생기지만 그건 "성사"가 아니라 모집 시작이고, 그 구간의 인원 변동은 `MATCH_PLAYERS_UPDATED`가 담당한다. 자리가 다 차도 앞당겨 쏘지 않는다 — 확정은 `max_member` 도달과 무관하게 마감 시각에만 일어난다(`feature-spec.md` 확정 판정)

#### `MATCH_ROOM_UPDATED` (SSE) — 매칭방 정보 갱신

- `data` = `RoomInfo` 전체 재전송 — 참가자 입장/퇴장/취소로 목록·팀 평균 페이스가 바뀔 때

#### 방 나가기 — 별도 이벤트 없음

- 확정된 방에서 나가기도 **`DELETE /users/me/running-match`** 사용 (5-A 참고 — 서버가 방 상태로 분기)
- 나간 사람만 `LEFT` 처리, 방 유지. 남은 인원은 `MATCH_ROOM_UPDATED`로 갱신한다 — 혼자 남아도 방은 유지되고 그대로 러닝을 진행한다
- **확정 후 이탈에는 페널티가 붙는다** — `close_at` + 유예 이후에 나가면 일정 시간 매칭 신청이 제한된다. 판정은 저장하지 않고 `left_at`·`close_at`으로 계산한다(`feature-spec.md` 페널티 절). 쿨다운 중 신청은 `409 MATCH_COOLDOWN`

#### 대기방 참여자 목록 — 별도 조회 없음

- `RoomInfo`가 참가자 전체를 담고 있고 변동 시마다 재전송되므로, 목록만 따로 받는 요청은 두지 않는다
- 앱 재시작 등으로 스트림이 끊겼다면 `GET /users/me/running-match`가 같은 정보를 돌려준다

### 5-C. 러닝 카운트 다운 — SSE에서 WebSocket으로

**카운트다운은 클라가 돌리되 기준 시각은 서버 값을 쓴다.** 타이머 구동과 화면 전환은 클라 몫이다 — 서버가 매초 틱을 보내지 않는다. 다만 기준을 각자 기기 시계로 삼으면 참가자마다 출발이 어긋나므로, 기준점만 서버에서 받아 보정한다.

**서버 시각은 별도 메시지 없이 HTTP 응답의 `Date` 헤더로 얻는다.** 매칭 API를 부를 때마다 받으므로 클라는 그 시점에 오프셋을 계산해 둔다. 오프셋 보정의 구체 방식(왕복 지연을 어떻게 빼는지)은 미정이다.

절차는 다음과 같다.

1. `scheduledStartAt` 직전(리드타임은 운영값)에 클라가 WS를 연결한다
2. 보정된 시각으로 **시작 3초 전부터 3-2-1 카운트다운**(화면·음성·햅틱). 이 구간에서는 뒤로가기를 차단한다
3. 도달 시 클라가 러닝 화면으로 전환하며 `RUNNING_START`(C→S)를 보낸다. 서버는 같은 시각에 스케줄러로 방 상태를 `STARTED`로 바꾼다
4. `RUNNING_STARTED` ack를 받으면 SSE 스트림을 닫는다

#### WebSocket 연결 — `/ws/running-rooms`

- **연결**: `wss://.../ws/running-rooms` + `Authorization: Bearer {accessToken}`
- **메시지 공통 형식**

```json
{
  "type": "...",
  "data": { ... }
}
```

- **ack 규칙**: 상태가 걸린 요청에만 — `RUNNING_START`→`RUNNING_STARTED`, `RUNNING_FINISH`→`RUNNING_FINISHED`
  - **`RUNNING_LOCATION_UPDATE`는 ack 없음**(보내고 끝 — 실패는 `ERROR`)
  - ack의 `data`는 비움
- **`ERROR` (S→C)** — WS 요청 실패 통지. REST 에러 포맷과 동일 계열

```json
{
  "code": "ROOM_NOT_FOUND",
  "message": "러닝 정보를 찾을 수 없습니다.",
  "sourceType": "RUNNING_LOCATION_UPDATE"
}
```

- **code**: `INVALID_REQUEST`(요청 검증 실패) / `ROOM_NOT_FOUND`(방 없음) / `NOT_ROOM_PLAYER`(참가자 아님) / `INVALID_ROOM_STATE`(현재 상태에서 불가한 요청)

#### `RUNNING_START` (C→S) — 러닝 시작 알림 (클라 주도)

```json
{
  "runningRoomId": 125
}
```

- 보정된 시각이 `startAt`에 도달하면 클라가 발신한다. 서버는 같은 시각에 스케줄러로 방 상태를 `STARTED`로 바꾸므로, 이 메시지는 상태 전환의 트리거가 아니라 개별 참가자의 시작 통보다
- **ack**: `RUNNING_STARTED` — 이걸 받으면 클라는 SSE 스트림을 닫는다

### 5-D. 러닝 중

#### `RUNNING_LOCATION_UPDATE` (C→S) — 위치 정보 전송 (10초 배치)

```json
{
  "runningRoomId": 125,
  "locations": [
    {
      "sequence": 15,                    // Long, 러닝 내 좌표 순번
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
  ]
}
```

- **클라는 1~2초 간격으로 수집해 로컬에 쌓으면서, 10초마다 모아서 보낸다.** 좌표 하나씩 10초마다 보내면 트랙이 성겨져 경로와 거리 정확도가 떨어진다
- 페이스·거리·케이던스·칼로리는 **클라가 계산한다**. 진행 시간도 클라 시각 기준이다(시작 시각만 5-C에서 서버 값으로 보정)
- 서버는 Redis(`sessionId+userId` 키)에 버퍼링 — 종료 시 S3 업로드(`gpsTrackKey`)
- **ack 없음** — 고빈도 메시지라 건별 ack는 트래픽 낭비. 실패는 `ERROR`로 통지
- **끊겼다 재연결하면 못 보낸 구간부터 이어 보낸다.** 클라는 마지막으로 전송에 성공한 `sequence`를 기억했다가, 재연결 후 그 다음 순번부터 로컬 사본을 다시 보낸다. 그래서 **로컬 사본은 종료할 때까지 지우지 않는다**
  - 서버는 이미 가진 `sequence`가 다시 오면 무시한다(멱등)
  - **한계**: 러닝이 끝날 때까지 연결이 돌아오지 않으면 그 기록은 잃는다 — `RUNNING_FINISH`조차 보낼 수 없기 때문이다. REST 폴백은 필요해지면 그때 붙인다

#### `PLAYER_RUNNING_PROGRESS_UPDATED` (S→C) — 참여자 진행 정보

```json
{
  "runningRoomId": 125,
  "players": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440015",
      "profileImageUrl": "https://...",
      "distanceMeters": 1520,               // 현재까지 이동 거리
      "targetDistanceMeters": 5000,         // 목표 거리(m)
      "currentPaceSecondsPerKm": 345,       // 현재 페이스(초/km)
      "paused": false                       // 일시정지 중이면 true
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440013",
      "profileImageUrl": "https://...",
      "distanceMeters": 1360,
      "targetDistanceMeters": 5000,
      "currentPaceSecondsPerKm": 372,
      "paused": true
    }
  ]
}
```

- `paused`가 없으면 상대가 멈춘 것과 느려진 것을 구분할 수 없다 — 화면에서 갑자기 뒤처진 것처럼 보인다

#### `COACHING_EVENT` (S→C) — 음성 코칭 이벤트

```json
{
  "runningRoomId": 125,
  "eventType": "OVERTAKEN",
  "targetUserId": "550e8400-e29b-41d4-a716-446655440013"
}
```

- **서버는 판정과 발신만 한다.** 음성 합성·재생·mute 토글은 전부 클라 몫이다
- 파티원과의 비교가 필요한 이벤트만 서버가 낸다(추월·역전·격차 등). **구간별 안내(1km 도달, 구간 기록)는 클라가 자기 데이터로 처리한다** — 서버가 관여할 이유가 없다
- 판정 기준을 서버가 독점하므로 참가자마다 다른 결과가 나오지 않는다. 클라가 각자 판정하면 같은 순간에 누구는 "추월당했다", 누구는 아무 일 없는 상태가 된다
- **트리거 조건(추월·역전 판정 기준, 격차 임계치)은 미정** — 정해지면 `eventType` 목록과 함께 이 절에 채운다

#### `RUNNING_PAUSE` / `RUNNING_RESUME` (C→S) — 일시정지·재개

```json
{
  "runningRoomId": 125
}
```

- **일시정지 동안 경과 시간과 거리 계산이 멈춘다.** 클라는 좌표 전송도 중단한다 — 멈춰 있는 동안의 좌표는 트랙에 남길 이유가 없고, GPS 흔들림이 거리로 잡히면 기록이 부풀려진다
- **다른 참가자는 계속 진행한다.** 일시정지는 본인 기록에만 영향을 주며 다른 참가자를 멈추지 않는다
- 서버는 상태를 다른 참가자에게 `PLAYER_RUNNING_PROGRESS_UPDATED`의 `paused` 필드로 알린다 — 상대가 멈췄는지 모르면 화면에서 갑자기 뒤처진 것처럼 보인다
- **ack 없음** — 실패는 `ERROR`로 통지

#### `RUNNING_FINISH` (C→S) — 러닝 종료 (정상/강제 통합)

```json
{
  "runningRoomId": 125,
  "forced": false
}
```

- `forced=true` = 목표 도달 전 즉시 종료 — 정상/강제의 서버 처리(현재까지 데이터로 기록 저장 + 러닝 종료)가 동일해 플래그로만 구분
- **이 시점에 서버가 `running_records`(+splits) 저장**. 거리·페이스·구간 분할 모두 **서버가 받은 좌표로 계산한다** — 클라 계산값은 러닝 중 화면 표시용이고 저장값이 아니다. GPS 트랙은 서버가 S3 업로드 + 다운샘플 `route_polyline` 생성
- **ack**: `RUNNING_FINISHED` — 수신 후 클라는 REST `GET /running-rooms/{id}/results`로 대시보드 진입
- 전원 제출 완료 or 타임아웃 중 먼저 오는 시점에 방 상태 `FINISHED` (타임아웃 값은 운영 정책)

## 6. 러닝 중 / 러닝 후 대시보드 (REST)

> **러닝 사진**: 앱에서 촬영해 디바이스 갤러리에만 저장 — 서버 업로드/조회 API 없음. results 등 응답에 사진 필드 없음.

### 6-1. `GET /api/v1/running-rooms/{runningRoomId}/results` — 러닝 종료 결과 (참가자 전원 요약)

- **화면**: 러닝 후 - 대시보드 (참가자 공통 정보). `RUNNING_FINISHED` 수신 후 진입
- **Response `200 OK`**

```json
{
  "runningRoomId": 125,
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
  "code": "NOT_ROOM_PLAYER",
  "message": "같은 방 참가자만 조회할 수 있습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요 (같은 방 참가자)

### 6-2. `GET /api/v1/running-rooms/{runningRoomId}/split-results` — 구간별 상세 + GPS 경로

- **화면**: 러닝 후 - 대시보드 (본인 경로 확인 + 참가자 상세·구간별 비교)
- **Response `200 OK`** (구조 요약)

```json
{
  "runningRoomId": 125,
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
          "caloriesKcal": 68
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
  "code": "NOT_ROOM_PLAYER",
  "message": "같은 방 참가자만 조회할 수 있습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요 (같은 방 참가자)

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
      "runningRoomId": 125,           // 솔로 러닝도 방을 만드므로 항상 값이 있다
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
- 같은 방 참가자 비교는 6-1·6-2(러닝 결과 API) 사용 — 이 API는 **본인 기록 전용**

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

> **솔로 러닝도 `runningRoomId`를 갖는다** — 매칭을 거치지 않을 뿐 방은 만들어진다(§5 참고). 따라서 7-1·7-2 응답의 `runningRoomId`는 항상 값이 있고, 두 API는 매칭·솔로 공통 조회다. 솔로 여부는 참가자 수로 구분한다.


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
  "visibility": "PUBLIC",              // FRIENDS | PUBLIC | PRIVATE
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

- **Query**: `tab=FRIENDS|ALL`(필수), `cursor`/`limit`
- **공개범위 필터**: `FRIENDS` = 친구의 `FRIENDS`/`PUBLIC` 피드 + 내 피드 전부, 최신순 / `ALL` = `PUBLIC` 피드 + 친구의 `FRIENDS` 피드, 최신순 + 가벼운 가중치(개인화 추천은 이후 확장)
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

- **에러 (403 Forbidden — 비공개 — `PRIVATE` 타인, `FRIENDS` 비친구)**

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
  "visibility": "PUBLIC",                  // 필수. FRIENDS|PUBLIC|PRIVATE — 기본 선택값 PUBLIC(클라 프리셋)
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

- **화면**: 전역 (앱 진입 시 `isOnboarded`로 홈/온보딩 분기, 로그인 직후 상세 조회, 편집 프리필)
- **Response `200 OK`**: `{ "userId", "nickname", "profileImageUrl", "introduction", "isOnboarded" }`
- **`email`은 내리지 않는다** — 이메일을 보여주는 화면은 설정 페이지의 계정 항목뿐이라 13-1이 담당한다. 이 응답은 앱을 열 때마다 타는 경로이므로 특정 화면에서만 쓰는 값을 싣지 않는다
- **인증**: 필요

### 11-2. `GET /api/v1/users/{userId}` — 프로필 요약

- **화면**: 프로필 (본인/타인 공통 — 본인이면 편집·설정 버튼, 타인이면 친구 요청 버튼 노출은 `isMe`로 분기)
- **Response `200 OK`**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440015",
  "isMe": false,
  "nickname": "동완러너",
  "profileImageUrl": "https://...",
  "introduction": "즐겁게 달려요",
  "friendCount": 42,                   // friendships에서 COUNT (status=ACCEPTED)
  "friendStatus": "ACCEPTED",          // NONE | PENDING_SENT | PENDING_RECEIVED | ACCEPTED
  "mileageTotalMeters": 320500,        // 누적 마일리지 — SUM(total_distance)
  "mileageMonthlyMeters": 42200,       // 이번 달 마일리지
  "bestPaceSecondsPerKm": 312,         // 최고 페이스 — MIN(avg_pace). 기록이 없으면 null
  "runningCount": 78                   // 러닝 횟수 — COUNT(*)
}
```

- **네 지표 모두 `running_records`에서 바로 계산한다** — 집계 테이블을 두지 않는다. `bestPaceSecondsPerKm`는 값이 작을수록 빠르므로 `MIN`이다
- **유효 러닝만 집계한다** — 최소 거리·최소 시간(운영 설정)에 미달하는 기록은 네 지표에서 제외한다. 기록 자체는 저장되고 본인 기록 목록·대시보드에는 보인다(`feature-spec.md` 유효 러닝 판정)
- **전체 사용자 대비 백분위는 내리지 않는다.** 순위 집계 배치와 저장소가 필요한데 초기에는 표본이 적어 수치 자체가 무의미하다(사용자 20명이면 "상위 12%"는 2등이라는 뜻이다). 나중에 응답 필드만 더하면 된다
- `friendStatus`로 버튼을 가른다 — `NONE`이면 "친구 요청", `PENDING_SENT`면 "요청 취소", `PENDING_RECEIVED`면 "수락", `ACCEPTED`면 "친구 삭제". 본인 프로필(`isMe=true`)이면 `null`이다

- **지인 마스킹**: `profile_visibility=FRIENDS`인 사용자를 친구가 아닌 사람이 조회하면 컬렉션 조회가 `403 PROFILE_PRIVATE`. 사진·닉네임·소개글·마일리지·최고 페이스·러닝 횟수·친구 수는 항상 공개. **친구 목록은 설정과 무관하게 본인만 본다**

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

- **공개범위**: 본인 = 전부(`PRIVATE` 포함) / 타인 = `PUBLIC` (+친구면 `FRIENDS`)
- **인증**: 필요

### 11-6. `POST /api/v1/users/{userId}/friend-request` — 친구 요청

- **Response `201 Created`**

```json
{
  "friendStatus": "PENDING"
}
```

- `friendships`에 `(요청자, 대상, PENDING)` 행을 만들고 대상에게 "친구 요청 도착" 푸시를 보낸다
- **역방향에 `PENDING`이 있으면 새 요청을 만들지 않고 수락으로 처리한다.** 서로 요청을 주고받은 상황은 이미 합의된 것이라 한 번 더 수락을 요구할 이유가 없다. 이때 응답은 `ACCEPTED`다
- **에러 (400 Bad Request)**: `CANNOT_FRIEND_SELF` — 자기 자신에게는 요청할 수 없다
- **에러 (409 Conflict)**: `FRIEND_REQUEST_ALREADY_EXISTS` — 이미 요청했거나 이미 친구다
- **에러 (404 Not Found)**: 대상이 없다
- **인증**: 필요

### 11-7. `DELETE /api/v1/users/{userId}/friend-request` — 요청 취소 · 거절

- **호출자가 보낸 쪽이면 취소, 받은 쪽이면 거절이다.** 이름만 다를 뿐 하는 일은 같아서(`PENDING` 행 DELETE) 하나로 둔다
- **이력을 남기지 않는다**
- **Response `204 No Content`**
- **에러 (404 Not Found)**: `PENDING` 요청이 없다
- **인증**: 필요

### 11-8. `POST /api/v1/users/{userId}/friend` — 요청 수락 / `DELETE` — 친구 삭제

- **POST(수락)**: 경로의 `{userId}`는 **요청을 보낸 사람**이다. `status`를 `ACCEPTED`로 바꾸고 요청자에게 "친구 요청 수락됨" 푸시를 보낸다
  - **Response `201 Created`**: `{ "friendStatus": "ACCEPTED" }`
  - **에러 (404 Not Found)**: 받은 요청이 없다
- **DELETE(친구 삭제)**: **양쪽 누구나 호출할 수 있다** — 성립한 뒤로는 방향에 의미가 없다. 이력을 남기지 않는다
  - **Response `204 No Content`**
  - **에러 (404 Not Found)**: 친구가 아니다
- **인증**: 필요

> **경로가 둘로 나뉜 이유**: `friend-request`는 `PENDING` 행만, `friend`는 `ACCEPTED` 행만 다뤄 이름이 상태와 어긋나지 않는다. 하나로 묶으면 DELETE 한 곳이 취소·거절·삭제를 겸해 핸들러에 상태 분기가 들어간다.
>
> | `friendStatus` | 화면 버튼 | 호출 |
> |---|---|---|
> | `NONE` | 친구 요청 | `POST .../friend-request` |
> | `PENDING_SENT` | 요청 취소 | `DELETE .../friend-request` |
> | `PENDING_RECEIVED` | 수락 / 거절 | `POST .../friend` / `DELETE .../friend-request` |
> | `ACCEPTED` | 친구 삭제 | `DELETE .../friend` |

### 11-9. `GET /api/v1/users/me/friends` — 친구 목록 / `GET /api/v1/users/me/friend-requests` — 받은 요청 목록

- **화면**: 친구 목록 페이지 (친구 탭 + 받은 요청 탭)
- **둘 다 본인 것만 조회한다.** 타인의 친구 목록은 열지 않는다 — 친구의 친구를 훑어 사람을 찾는 흐름이 없고(사람 찾기는 `GET /users/search`), 누구와 친구인지는 민감한 정보다. 타인 프로필에는 **친구 수만** 표시된다
- **Query**: `q`(이름 필터, 친구 목록만), `cursor`/`limit`
- **Response `200 OK`**: `{ "items": [ { "userId", "nickname", "profileImageUrl" } ], "nextCursor": "..." }`
- 친구 목록은 `status='ACCEPTED'`이면서 `requester_id`·`receiver_id` 중 하나가 본인인 행이다 — **성립한 뒤로는 방향에 의미가 없어 두 컬럼을 모두 본다**

```sql
SELECT receiver_id  AS friend_id FROM friendships WHERE requester_id = :me AND status = 'ACCEPTED'
UNION ALL
SELECT requester_id AS friend_id FROM friendships WHERE receiver_id  = :me AND status = 'ACCEPTED'
```

- `OR`로 묶지 않고 `UNION ALL`을 쓴다 — `OR`는 인덱스를 타지 못하고, 이 형태는 `requester_id`(PK 앞자리)와 `receiver_id`(보조 인덱스)를 각각 탄다
- 받은 요청 목록은 본인이 `receiver_id`이고 `status='PENDING'`인 행이다. 보낸 요청 목록은 화면이 없어 API도 두지 않는다
- **인증**: 필요

### 11-10. `GET /api/v1/users/{userId}/colors` — 컬러 컬렉션

- **화면**: 프로필 — 획득한 색을 `보유 수 / 전체 수`와 함께 보여준다
- **Response `200 OK`** — 마스터 전체를 내리고 각 색에 획득 여부를 표시한다

```json
{
  "ownedCount": 17,
  "totalCount": 30,
  "colors": [
    {
      "colorId": 2,
      "category": "ENDURANCE",
      "shade": 2,
      "name": "딥 블루",
      "hex": "#3c62e2",
      "description": "10km 이상 완주",
      "owned": true,
      "acquiredAt": "2026-08-01T09:12:00"
    },
    {
      "colorId": 3,
      "category": "ENDURANCE",
      "shade": 3,
      "name": "심해 블루",
      "hex": "#1a3a8f",
      "description": "누적 100km",
      "owned": false,
      "acquiredAt": null
    }
  ]
}
```

- **못 얻은 색도 함께 내린다.** 컬렉션 화면은 "무엇을 더 모을 수 있는지"를 보여주는 것이 목적이라, 미획득 색과 그 조건(`description`)이 있어야 화면이 성립한다
- `totalCount`는 마스터 행 수다 — **총 개수를 명세에 박지 않으므로** 클라도 이 값을 그대로 쓴다
- **지인 마스킹**: `profile_visibility=FRIENDS`인 사용자를 친구가 아닌 사람이 조회하면 `403 PROFILE_PRIVATE`
- **에러 (404 Not Found)**: 대상이 없다
- **인증**: 필요

### 11-11. `GET /api/v1/users/search` — 사용자 검색

- **화면**: 러너 검색 — **친구를 추가하려면 먼저 사람을 찾아야 하므로 친구 기능의 진입점이다**
- **Query**: `q`(필수, 닉네임), `cursor`/`limit`
- **Response `200 OK`**: `{ "items": [ { "userId", "nickname", "profileImageUrl", "friendStatus" } ], "nextCursor": "..." }`
- `friendStatus`는 `NONE`/`PENDING_SENT`/`PENDING_RECEIVED`/`ACCEPTED` — 버튼을 무엇으로 그릴지가 이 값에 달렸다. 보낸 요청과 받은 요청을 구분해야 "요청 취소"와 "수락"이 갈린다
- **인증**: 필요

## 12. 프로필 편집 페이지

### 12-1. `POST /api/v1/users/{userId}/profile-image/presigned-url` — 프로필 사진 업로드 URL

- **Request**

```json
{
  "mimeType": "image/jpeg",
  "fileSizeBytes": 204800
}
```

| 필드 | 타입 | 제약 |
|---|---|---|
| `mimeType` | String | 필수. `image/jpeg`·`image/png`·`image/webp`만 허용(대소문자 무시) |
| `fileSizeBytes` | Long | 필수. 1 이상 10,485,760(10MB) 이하 — 서명에 포함되므로 실제 업로드 크기와 같아야 한다 |

- **Response `200 OK`**

```json
{
  "profileImageKey": "profiles/550e8400-.../9f1c2b7e-....jpg",
  "uploadUrl": "https://..."
}
```

`profileImageKey` 포맷은 `profiles/{userId}/{imageId}.{확장자}` — 소유자 검증에 쓰이므로 클라가 임의로 만들지 않는다. 확장자는 `mimeType`이 정한다(`image/jpeg`→`jpg`, `image/png`→`png`, `image/webp`→`webp`). 클라는 `uploadUrl`로 S3에 직접 업로드하며, 업로드 헤더의 `Content-Type`은 요청한 `mimeType`과 일치해야 한다(서명에 포함).

- **에러 (403 Forbidden — 본인 아님)**

```json
{
  "code": "ACCESS_DENIED",
  "message": "본인만 요청할 수 있습니다."
}
```

- **인증**: 필요 (본인만 — `{userId}`가 토큰 주체와 다르면 403)

### 12-2. `PATCH /api/v1/users/{userId}/profile-image` — 프로필 사진 반영

12-1로 받은 `uploadUrl`에 업로드를 마친 뒤 호출한다. 서버가 S3에 실제로 올라왔는지 확인하고 `users.profile_image_key`를 갱신한다.

- **Request**

```json
{
  "profileImageKey": "profiles/550e8400-.../9f1c2b7e-....jpg"
}
```

| 필드 | 타입 | 제약 |
|---|---|---|
| `profileImageKey` | String | 필수, 255자 이하. 12-1이 발급한 키 그대로 |

- **Response `200 OK`**

```json
{
  "profileImageKey": "profiles/550e8400-.../9f1c2b7e-....jpg"
}
```

- **에러 (400 Bad Request — 본인 키가 아니거나 형식이 어긋남)**

```json
{
  "code": "INVALID_PROFILE_IMAGE",
  "message": "프로필 이미지가 올바르지 않습니다."
}
```

- **에러 (400 Bad Request — 키에 해당하는 객체가 S3에 없음)**

```json
{
  "code": "PROFILE_IMAGE_NOT_UPLOADED",
  "message": "업로드되지 않은 이미지입니다."
}
```

- **에러 (403 Forbidden — 본인 아님)**

```json
{
  "code": "ACCESS_DENIED",
  "message": "본인만 요청할 수 있습니다."
}
```

- **인증**: 필요 (본인만)

### 12-3. `GET /api/v1/users/{userId}/profile-image` — 프로필 사진 URL 조회

- **Response `200 OK`**

```json
{
  "profileImageUrl": "https://..."
}
```

사진이 등록돼 있지 않으면 `profileImageUrl`은 `null`이다.

- **에러 (400 Bad Request — 대상 사용자 없음)**

```json
{
  "code": "PROFILE_NOT_FOUND",
  "message": "사용자를 찾을 수 없습니다."
}
```

- **인증**: 불필요

### 12-4. `DELETE /api/v1/users/{userId}/profile-image` — 프로필 사진 삭제

`users.profile_image_key`를 비운다. **S3 객체는 지우지 않고 DB의 키 연결만 끊는다.** 사진이 없는 상태에서 호출해도 에러 없이 성공한다(idempotent).

- **Request**: 본문 없음
- **Response `204 No Content`**

- **에러 (403 Forbidden — 본인 아님)**

```json
{
  "code": "ACCESS_DENIED",
  "message": "본인만 요청할 수 있습니다."
}
```

- **인증**: 필요 (본인만)

### 12-5. `PATCH /api/v1/users/me` — 프로필 수정

- **Request**: `{ "introduction"? }` (부분 수정). 닉네임은 12-6, 사진은 12-1~12-4로 각각 전용 엔드포인트를 쓴다. 키·몸무게 수정은 **[MVP 제외]**, 평균 페이스는 수정 불가(서버 자동 갱신)
- **Response `200 OK`**: 11-1 형태 갱신본

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "입력값이 올바르지 않습니다."
}
```

- **인증**: 필요

### 12-6. `PATCH /api/v1/users/{userId}/nickname` — 닉네임 변경

닉네임은 `user_onboardings.nickname`에 있어 온보딩을 마쳐야 바꿀 수 있다. 서비스 전반의 표시명이 이 값이다.

- **Request**

```json
{
  "nickname": "완두콩"
}
```

| 필드 | 타입 | 제약 |
|---|---|---|
| `nickname` | String | 필수, 2~16자, 한글·영문·숫자·`_`만 |

- **Response `200 OK`**

```json
{
  "userId": "550e8400-...-440001",
  "nickname": "완두콩"
}
```

현재 닉네임과 같은 값을 보내면 아무것도 바꾸지 않고 그대로 반환한다(idempotent).

- **에러 (409 Conflict — 남이 쓰고 있음)**

```json
{
  "code": "NICKNAME_ALREADY_EXISTS",
  "message": "이미 사용 중인 닉네임입니다."
}
```

- **에러 (409 Conflict — 온보딩 미완료)**

```json
{
  "code": "ONBOARDING_NOT_COMPLETED",
  "message": "온보딩을 먼저 완료해 주세요."
}
```

- **에러 (403 Forbidden — 본인 아님)**

```json
{
  "code": "ACCESS_DENIED",
  "message": "본인만 요청할 수 있습니다."
}
```

- **인증**: 필요 (본인만)

### 12-7. `POST /api/v1/users/nickname/availability` — 닉네임 중복 확인

저장하기 전에 쓸 수 있는 닉네임인지 미리 확인한다. 확인과 저장 사이에 남이 선점할 수 있으므로 최종 방어는 12-6·1-9의 409다. — 사용 화면: 프로필 편집, 온보딩

- **Request**

```json
{
  "nickname": "완두콩"
}
```

| 필드 | 타입 | 제약 |
|---|---|---|
| `nickname` | String | 필수, 2~16자, 한글·영문·숫자·`_`만 |

- **Response `200 OK`**

```json
{
  "nickname": "완두콩",
  "available": true
}
```

- **에러 (400 Bad Request)** — 검증 실패 시 `code`는 `INVALID_REQUEST` 공통, `message`로 사유 구분

```json
{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 2자 이상 16자 이하여야 합니다."
}
```

- **인증**: 불필요

## 13. 설정 페이지

### 13-1. `GET /api/v1/users/me/account` — 계정 정보

- **화면**: 설정 (계정 항목)
- **Response `200 OK`**

```json
{
  "email": "run@example.com",
  "loginType": "GOOGLE"                  // LOCAL | GOOGLE | KAKAO
}
```

- **`loginType` 판정**: `oauth_users`에 row가 있으면 그 `provider`, 없으면 `LOCAL`. `users.password_hash`의 null 여부로 판정하지 않는다 — 결과는 같지만 "무슨 계정인가"에 직접 답하는 데이터는 `oauth_users`다
- **계정은 로컬·소셜 중 하나로 배타적이다** — 소셜 최초 가입 시 이메일이 기존 로컬 계정과 겹치면 자동 연동하지 않고 `409`로 거부한다(1-5/1-6). 그래서 단일 값으로 표현된다
- **클라 표시 규칙**: `LOCAL`이면 로그인 수단 문구 없이 "비밀번호 변경" 메뉴를 노출한다. 소셜이면 "구글/카카오 계정으로 로그인 중"을 표시하고 비밀번호 변경 메뉴를 감춘다. 로컬에 "이메일 계정" 같은 문구를 붙이지 않는 이유 — 바로 위에 이메일이 떠 있고, 비밀번호 변경 메뉴의 존재 자체가 이미 로컬이라는 표시다. 소셜 문구가 필요한 건 어느 provider로 가입했는지 잊으면 다른 버튼을 눌러 별개 계정이 되기 때문이다
- **인증**: 필요

### 13-2. `PATCH /api/v1/users/{userId}/password` — 비밀번호 변경

로컬 계정만 가능. 현재 비밀번호로 본인을 재확인한다.

- **화면**: 설정 (계정 항목 → 비밀번호 변경)
- **Request**

```json
{
  "currentPassword": "********",                                          // 필수
  "newPassword": "********"                                               // 필수 — 6~16자, 영문·숫자·특수문자 각 1자 이상 (확인 일치 검증은 클라이언트)
}
```

- **Response**: `204 No Content`
- **기존 토큰은 무효화하지 않는다** — 다른 기기 세션이 유지된다. 변경 즉시 전 기기 로그아웃은 **[MVP 제외]**
- **새 비밀번호가 현재와 같아도 거부하지 않는다** — 별도 검증을 두지 않는다

- **에러 (400 Bad Request)** — 검증 실패 시 `code`는 `INVALID_REQUEST` 공통, `message`로 사유 구분

```json
{
  "code": "INVALID_REQUEST",
  "message": "현재 비밀번호는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 6자 이상 16자 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다."
}
```

- **에러 (401 Unauthorized — 현재 비밀번호 불일치)**

```json
{
  "code": "INVALID_CURRENT_PASSWORD",
  "message": "현재 비밀번호가 올바르지 않습니다."
}
```

- **클라이언트는 이 `401`을 토큰 만료로 오인하면 안 된다.** 액세스 토큰 문제는 `TOKEN_EXPIRED`·`TOKEN_BLOCKED`·`INVALID_TOKEN`·`AUTHENTICATION_REQUIRED`이며, `code`가 `INVALID_CURRENT_PASSWORD`이면 refresh 후 재시도하지 말고 입력 오류로 처리한다

- **에러 (409 Conflict — 소셜 계정)**

```json
{
  "code": "PASSWORD_NOT_SET",
  "message": "소셜 로그인으로 가입한 계정은 비밀번호를 변경할 수 없습니다."
}
```

- 클라는 13-1의 `loginType`으로 메뉴를 감추지만 서버도 막는다 — 구버전 앱과 직접 호출이 있다

- **에러 (403 Forbidden — 본인 아님)**

```json
{
  "code": "ACCESS_DENIED",
  "message": "본인만 요청할 수 있습니다."
}
```

- **인증**: 필요 (본인만 — `{userId}`가 토큰 주체와 다르면 403)

> **비밀번호 찾기(로그인 전 재설정)는 명세에 없다** — 로그인 화면에 진입점이 없다. 필요해지면 이메일 인증(1-1/1-2)의 `verificationTicket`을 받는 별도 엔드포인트로 정의한다. 이 API는 로그인된 상태 전용이다.

### 13-3. `GET /api/v1/users/me/settings` — 설정 조회

- **화면**: 설정
- **Response `200 OK`**

```json
{
  "alertConsent": true,                  // 전체 알림 on/off (단일 토글, 기본 on)
  "profileVisibility": "PUBLIC"          // FRIENDS | PUBLIC — 지인 마스킹 on/off
}
```

- **`alertConsent` = 단일 토글** — 매칭 확정/실패, 러닝 시작 리마인더, 친구 요청 도착/수락을 한 번에 on/off (`users.alert_consent`). **기본값 `true`**, OS 알림 권한과는 별개로 동작한다(둘 중 하나라도 꺼져 있으면 미도달)
- **공개범위 설정**: `profileVisibility`(FRIENDS/PUBLIC — 지인 마스킹 on/off). `feedDefaultVisibility`(피드 작성 기본값)는 **[MVP 제외]** — 피드 기본값은 클라 PUBLIC 프리셋
- **인증**: 필요

### 13-4. `PATCH /api/v1/users/me/settings` — 설정 변경

- **Request**: 13-3 필드 부분 수정 / **Response `200 OK`**: 갱신본
- **인증**: 필요

### 13-5. `DELETE /api/v1/users/me` — 회원탈퇴

- **화면**: 설정 (확인 팝업 후)
- **동작 (테이블별 정책)**: `delete_users` 스냅샷(email/alertConsent/createdAt) → `users` 하드delete. **유지**: `feeds`/`comments`/`running_records`(+splits)/좋아요(카운트 유지) — 작성자는 "탈퇴한 사용자" 고정 표시. **CASCADE 삭제**: `friendships`(요청·수락 양쪽 모두 — 친구 수는 COUNT라 재계산이 필요 없다). **삭제**: `user_onboardings`/`user_devices`/`oauth_users`/`user_running_contests`/`running_players`
- **Response**: `204 No Content` (토큰 즉시 무효화)
- **인증**: 필요
