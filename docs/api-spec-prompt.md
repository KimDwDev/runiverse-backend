너는 시니어 백엔드 API 설계자야. 아래 4가지 자료를 기반으로 REST API 명세서를 작성해줘.

## 입력 자료
입력 자료(기능명세서/와이어프레임/ERD/기획서)는 별도로 붙여넣지 않고 `api-spec-context.md` 스냅샷을 사용.
**주의: 원본 자료는 프로젝트 진행에 따라 계속 바뀜 — 쓸 때마다 스냅샷이 최신인지 확인하고, 의심되면 원본(기능명세서.docx, ERD 캡처, 기획서.docx)을 다시 반영한 뒤 진행할 것.**

## 담당 범위
담당 범위는 따로 나누지 않기로 확정 — 항상 **전체 화면 기준**으로 전체 엔드포인트 스펙을 작성해줘.

## 기존 산출물
직전 완성본 `api-spec-v1.md` 있음(계속 갱신 중 — 현재 파일이 최신 정본). 처음부터 새로 만들지 말고 **기존 `api-spec-v1.md`를 기준으로 바뀐 부분만 갱신**. (완전히 새 화면/기능 추가 시에만 아래 작업 방식의 1단계부터 적용)

## 작업 방식
1. 먼저 기능명세서를 화면 단위로 훑으면서, 각 화면에서 필요한 API 엔드포인트 목록을 뽑아줘.
   - 화면 이름 → 필요한 API들 (예: "GET /feeds", "POST /feeds") 형태로 그룹핑
   - **같은 API가 여러 화면에서 공통으로 필요하면 화면마다 반복해서 나열하지 말고, 한 번만 정의하고 "사용 화면: OO, XX" 형태로 표시해줘.**
   - 이 단계에서는 목록만 제시하고, 상세 스펙은 아직 쓰지 마
   - 화면명세서만으로 판단 안 되는 부분(예: 정렬 기준, 권한 범위)은 추측하지 말고 질문 목록으로 따로 정리해줘
   - **아래 "공통 컨벤션"은 이미 확정되어 있으니 다시 질문하지 말고 그대로 적용해줘.**
2. 내가 목록을 확인하고 피드백을 알려주면, 그 다음에 **1단계에서 뽑은 전체 엔드포인트**에 대해 상세 명세를 작성해줘.

## 공통 컨벤션 (확정됨 — 다시 묻지 말고 그대로 적용해줘)
- Base path: `/api/v1`
- 날짜/시간 포맷: ISO 8601, UTC (예: `2026-07-20T04:00:00Z`)
- 에러 응답 공통 포맷: `{ code, message }` (평면 구조 — `error` 래핑 없음, status 필드도 없음, HTTP 상태 코드로만 표현)
- 인증 방식: Bearer 토큰, Access+Refresh 토큰 이원화 (`Authorization: Bearer {accessToken}` 헤더). Refresh 시 rotation(access·refresh 토큰 **모두 재발급**, 이전 refreshToken 무효). 로그아웃 시 해당 access 토큰은 서버 블랙리스트 처리(`401 TOKEN_BLOCKED`)
- 페이지네이션: Cursor 기반 (`?cursor=&limit=`), 응답은 `{ items: [...], nextCursor: string | null }`
- 토글형 액션(팔로우/좋아요 등): POST(등록)/DELETE(취소) 분리, 응답에 갱신된 카운트 포함(재조회 방지)
- 필드 네이밍: **API 표면(JSON req/res)은 `camelCase` 통일** — DB 컬럼은 Postgres `snake_case` 유지, 백엔드(Spring)에서 매핑.
- enum 값: **DB·API 동일한 영문 코드** (예: `"visibility": "PUBLIC"`) — 변환 매핑 없음, 값 목록은 `erd.md` §6(enum 사전) 참고.
- 물리량 필드는 **단위 접미 풀네임 명시(예외 0)**: `...Meters` / `...Seconds` / `...SecondsPerKm` / `...MetersPerSecond` / `...Degrees` / `...Kg` / `...Cm` — 통용어 `Spm`(케이던스)만 약어 예외. **거리는 전부 미터 통일**(km 표시는 프론트 포맷팅), 페이스는 초/km 정수(`390`→"6:30" 표시) (예: `totalDistanceMeters`, `averagePaceSecondsPerKm`, `speedMetersPerSecond`, `weightKg`)

**구체적인 결정사항**(매칭·러닝 WebSocket 설계, 소셜 로그인 provider/방식, 회원가입·온보딩 닉네임 위치, 탈퇴 유저 표시 등)은 `api-spec-context.md`에 있음. **DB 스키마(테이블·컬럼·타입·PK 규칙·enum·단위)는 `erd.md`가 단일 출처.** 여기 중복 안 함, 스펙 작성 시 두 파일 기준으로 적용.

## 상세 명세 포맷 (엔드포인트당)
- **Method + Path** (리소스 중심 네이밍, 예: `GET /users/{userId}/feeds`)
- **설명**: 이 API가 어떤 화면의 어떤 동작에 대응하는지 (기능명세서 항목 참조)
- **Request**
  - Path/Query 파라미터
  - Request Body (JSON, 필드명/타입/필수여부)
- **Response**
  - 성공 응답 Body (JSON, 필드명/타입) — ERD 컬럼의 **의미**와 최대한 일치시키되 표기는 camelCase로 변환(예: `total_distance` → `totalDistance`), 클라이언트에 불필요한 내부 컬럼(FK, deleted_at 등)은 제외
  - 상태 코드 (200/201/204 등)
- **에러 케이스**: 발생 가능한 4xx/5xx와 상황
- **인증/권한**: 로그인 필요 여부, 본인 소유 리소스 체크 필요 여부

## 유의사항
- ERD에 없는 필드를 임의로 만들지 말고, **`erd.md`의 컬럼을 기준으로** Request/Response를 구성해줘(단, 필드 표기는 camelCase로 변환 — DB 컬럼명 그대로 쓰지 말 것). `erd.md`에 없지만 필요해 보이는 필드가 있으면 "ERD에 없음, 확인 필요"로 표시해줘.
- 도메인 제약은 `api-spec-context.md` 3번(ERD·도메인 제약 섹션)을 따라줘 — 대표적으로: running_record는 종료 시점 일괄 INSERT(진행 중 PATCH 없음), 이미지 업로드는 Presigned 2단계, GPS 트랙은 Redis 버퍼 → 서버가 S3 업로드. **스키마(테이블·컬럼·타입)는 `erd.md`가 단일 출처.**
- 화면 간 이동은 페이지 이름으로 참조돼 있으니(예: "피드 상세 페이지로 이동") 명세서에서도 화면 이름 기준으로 정리해줘.
- 애매하거나 기능명세서에 명시 안 된 부분은 임의로 결정하지 말고 질문으로 남겨줘.
