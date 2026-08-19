# API Convention

REST API 표면 규칙 — 엔드포인트 설계·DTO 작성·스펙 문서화가 모두 이 규칙을 따른다.

## 기본

- Base path: `/api/v1`
- 필드명: JSON 요청/응답은 camelCase — DB 컬럼은 snake_case 유지, 백엔드에서 매핑한다.
- ID 타입: `userId`만 UUID, 서버가 발급하는 그 외 리소스 ID는 Long이다. 클라이언트 식별자(`deviceId`)와 커서(`cursor`)·S3 key는 문자열이다.
- 시각: ISO 8601 `yyyy-MM-ddTHH:mm:ss` — KST 기준, 오프셋 없이 초 단위까지(예: `2026-07-20T13:00:00`). 달력 날짜는 `YYYY-MM-DD`다. 저장은 KST(`TimeZoneConfig`), 직렬화 형식은 `JacksonConfig`가 고정한다.
- DB enum: API에 노출할 때도 동일한 영문 코드를 사용한다. 변환 매핑은 없고 값 목록은 [erd.md](erd.md) §6을 따른다.

## 하위 호환

- 클라이언트가 스토어 배포 앱이라 구버전이 오래 남는다 — 이미 배포된 계약은 파괴적으로 바꾸지 않는다.
- 필드 추가는 optional로 한다. 기존 필드는 제거·리네임·타입 변경·의미 변경을 하지 않는다.
- 바꿔야 하면 새 필드를 더하고, 구버전이 빠질 때까지 기존 필드도 함께 채운다.

## 응답 형태

- 에러 응답: `{ code, message }`는 필수이며 오류별 추가 필드를 둘 수 있다. 평면 구조를 쓰고 `error` 래핑·status 필드는 두지 않으며, 상태는 HTTP 상태 코드로 표현한다.
- 페이지네이션을 사용하는 목록은 커서 기반(`?cursor=&limit=`)이다. `limit` 기본값은 20, 최대 50이며 초과값은 50으로 클램프한다. 응답은 `{ items: [...], nextCursor: string | null }`이다.
- 토글형 액션: POST(등록)/DELETE(취소)로 분리하며 중복 호출에도 성공한다. 갱신된 상태·카운트를 반환하면 `200 OK`, 반환값이 없으면 `204 No Content`로 응답한다.

## 인증

- Bearer 토큰, Access+Refresh 이원화(`Authorization: Bearer {accessToken}`).
- Refresh 시 rotation: access·refresh 모두 재발급하고 이전 refreshToken은 무효화한다.
- 로그아웃 시 해당 access 토큰은 서버 블랙리스트 처리한다(`401 TOKEN_BLOCKED`).

## 물리량 단위

- 단위 접미사는 `...Meters` / `...Seconds` / `...SecondsPerKm` / `...MetersPerSecond` / `...Degrees` / `...Bytes` / `...Kg` / `...Cm` / `...Kcal` / `...Spm`으로 명시한다. 위도·경도는 표준 필드명 `latitude`/`longitude`를 쓴다.
- 거리는 전부 미터로 통일하고(km 표시는 프론트 포맷팅), 페이스는 초/km 정수로 쓴다(`390` → "6:30" 표시).
- 예: `totalDistanceMeters`, `averagePaceSecondsPerKm`, `speedMetersPerSecond`, `weightKg`, `caloriesKcal`
