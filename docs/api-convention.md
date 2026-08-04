# API Convention

REST API 표면의 확정 규칙. 엔드포인트 설계·DTO 작성·스펙 문서화 모두 이 규칙을 따른다.

## 기본

- Base path: `/api/v1`
- 필드명: JSON 요청/응답은 camelCase — DB 컬럼은 snake_case 유지, 백엔드에서 매핑
- ID 타입: `userId`만 UUID, 그 외 ID는 Long
- 날짜/시간: ISO 8601, UTC (예: `2026-07-20T04:00:00Z`)
- enum: DB·API 동일한 영문 코드 (예: `"visibility": "PUBLIC"`) — 변환 매핑 없음, 값 목록은 [erd.md](erd.md) §6(enum 사전)

## 응답 형태

- 에러 응답: `{ code, message }` 평면 구조 — `error` 래핑·status 필드 없음, HTTP 상태 코드로만 표현
- 페이지네이션: Cursor 기반 (`?cursor=&limit=`), 응답은 `{ items: [...], nextCursor: string | null }`
- 토글형 액션(팔로우/좋아요 등): POST(등록)/DELETE(취소) 분리, 응답에 갱신된 카운트 포함(재조회 방지)

## 인증

- Bearer 토큰, Access+Refresh 이원화 (`Authorization: Bearer {accessToken}`)
- Refresh 시 rotation: access·refresh 모두 재발급, 이전 refreshToken 무효
- 로그아웃 시 해당 access 토큰은 서버 블랙리스트 처리 (`401 TOKEN_BLOCKED`)

## 물리량 단위

- 단위 접미 풀네임 명시(예외 0): `...Meters` / `...Seconds` / `...SecondsPerKm` / `...MetersPerSecond` / `...Degrees` / `...Kg` / `...Cm` — 통용어 `Spm`(케이던스)만 약어 예외
- 거리는 전부 미터 통일 (km 표시는 프론트 포맷팅), 페이스는 초/km 정수 (`390` → "6:30" 표시)
- 예: `totalDistanceMeters`, `averagePaceSecondsPerKm`, `speedMetersPerSecond`, `weightKg`
