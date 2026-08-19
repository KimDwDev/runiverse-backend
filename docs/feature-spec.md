# Runiverse 기능 명세서

## 1. 화면별 기능

> 화면 순서는 사용자 흐름 기준: 인증·온보딩 → 홈·매칭 → 러닝 → 기록·대회 → 피드 → 프로필·설정 → 공통.

### 인증·온보딩

**초기 페이지**

- 로그인 폼: 이메일·비밀번호 입력 필드 제공.
- 로그인 버튼: 입력한 이메일/비밀번호로 로그인 수행.
- 회원가입 버튼 → 회원가입 페이지로 이동.
- Google / Kakao 로그인 버튼: 소셜 계정으로 회원가입 또는 로그인. 네이버는 1차 미포함.
  - 인가 코드 + PKCE 방식: 앱이 provider 인증 후 인가 코드(`authorizationCode`)·`codeVerifier` 수령 → `POST /auth/oauth/google|kakao`로 서버 전달 → 서버가 provider와 코드↔토큰 교환(PKCE `codeVerifier` 검증) → 유저 정보 조회 → `provider_id`로 `oauth_users` 조회(없으면 생성=회원가입) → 자체 토큰 발급. 카카오는 SDK 앱 전환, 구글은 커스텀 탭으로 인가를 시작하고 커스텀 스킴으로 인가 코드를 돌려받는다 — 웹뷰는 provider가 차단한다.
  - 최초 가입 시 온보딩 화면을 거침 — `oauth_users` 생성 후 `user_onboardings` 입력 필요.
  - 카카오 이메일 미동의 시 403 가입 거부(`OAUTH_EMAIL_NOT_PROVIDED` — `users.email` NOT NULL 유지).

**회원가입 페이지**

- 회원가입 폼: 이메일, 인증번호, 비밀번호, 비밀번호 확인. 닉네임·약관 동의는 온보딩에서 받음 — `termsAgreed`는 signup 요청에 없음.
- 이메일 인증 3단계: 인증번호 발송 → 확인(`verificationTicket` 발급) → 티켓+비밀번호로 가입. 이메일은 티켓에서 꺼내 쓰므로 가입 요청에 담지 않음.
  - 이미 가입된 이메일은 발송 단계에서 409 차단 — 가입 여부 노출보다 UX를 택함.
  - 코드 5분·시도 5회, 재발송 쿨다운 30초·하루 10회, 티켓 30분 1회용.
- 회원가입 버튼: 입력 정보 검증 후 회원가입 수행. 가입 즉시 자동 로그인(토큰 발급) — 로그인 화면 거치지 않고 온보딩으로 이동.

**온보딩 화면**

- 온보딩 폼: 약관 동의 체크 + 닉네임, 생년월일, 성별, 키, 몸무게, 평균 페이스 입력 후 제출.
  - 약관 동의는 로컬·소셜 공통 관문 — 가입 경로 무관 온보딩에서 일괄 수취, 동의 시각 증빙은 `user_onboardings.created_at`
  - 닉네임 중복 체크(`NICKNAME_ALREADY_EXISTS`)는 온보딩 API의 에러 케이스.
  - 1회성 입력 — 1차는 닉네임만 `PATCH /users/me`로 수정 가능, 키·몸무게 수정은 2차. 평균 페이스는 수정 UI 없이 서버가 러닝 기록 기반 자동 갱신.

**하단 네비게이션 바**

- 홈 / 대회 일정 / 피드 / 기록 / 프로필 5개 버튼으로 각 화면 이동.

### 홈·매칭

**홈 화면**

- 날씨 정보: 현재 위치 기준 날씨 제공.
- 매칭 버튼: 설정된 조건으로 러닝 매칭 시작.
- 매칭 설정 버튼: 매칭 조건 설정 모달 오픈.
- 매칭 정보 입력 모달: 거리/시간 입력.
  - 모집 인원은 입력받지 않음 — 서버가 2~4명 범위에서 자동 편성. 친구 초대(친구 선택 모달)는 1차 미포함, 랜덤 매칭만.
  - `running_players` row 생성이 곧 매칭 요청 저장. "시간"은 목표 러닝 시간이 아니라 희망 시작 시각(예약 매칭) — `running_players.start_at`에 저장. "거리"는 `running_players.target_distance`(플레이어별 목표 거리)에 저장.
  - 요청과 동시에 방이 정해진다 — 조건이 맞는 모집 중인 방(`type='MATCH'`, `current_member < max_member`)이 있으면 합류하고, 없으면 **본인만 있는 1인 방을 새로 만든다**. 즉 대기 중에도 항상 방이 존재한다.
- 매칭 대기 화면: 매칭 진행 상태·남은 예상 시간·현재 참가자 확인.
  - 확정/취소 판정: 판정 시각은 방 생성 시 `running_rooms.close_at`(= `start_at` - 서버 설정값)으로 **고정 저장**한다. 설정을 바꿔도 이미 만들어진 방의 마감은 움직이지 않는다. `close_at` 도달 시 `current_member >= 2`면 `status='MATCHED'` 자동 확정, `1`이면 `status='CANCELLED'` 자동 취소 — 자리 수(`max_member`) 충족 여부와 무관.

**매칭완료 대기방**

- 현재 위치 지도: 사용자 위치 지도 표시.
- 매칭된 참가자 목록: 닉네임·페이스·소개글 표시 — 뱃지는 프로필 화면에서만 노출.
- 이모티콘 주고받기: 대기방 참가자끼리 전송·수신 (WS `EMOJI_SEND`(C→S) / `EMOJI_RECEIVED`(S→C)).
- 시작 타이머: 러닝 시작까지 카운트다운 — 클라이언트 주도 시작.
  - 카운트다운은 각 클라이언트가 자체 시계 기준으로 표시하고, `start_at` 도달 시 스스로 러닝 화면으로 전환하며 WS `RUNNING_START`(C→S)로 시작을 알림.
  - 서버는 같은 시각에 내부 스케줄러로 `running_rooms.status='STARTED'` 전환 — 클라이언트가 호출하는 REST API 없음.
- 나가기: 나간 사람만 `running_players.status`를 `MATCHED_LEFT_PENALTY`/`MATCHED_LEFT_NO_PENALTY`로 전환하고 `deleted_at`을 찍는다. 방은 유지되고 남은 참가자는 계속 대기(`current_member` 감소).
- 확정(`MATCHED`) 이후 이탈 시 페널티 부여 — 페널티 여부는 이탈 시점에 서버가 판정해 `running_players.status`에 고정 저장한다(별도 페널티 테이블 없음). 이탈로 `current_member`가 2 미만이 되면 방 취소, 자동 재매칭(빈자리 채우기)은 하지 않음.

### 러닝

**러닝 중 - 메인**

- 위치 지도: 현재 위치·이동 경로 표시.
- 러닝 정보: 페이스·러닝 시간·이동 거리·케이던스 실시간 제공.
- 사진 촬영: 촬영본은 본인 디바이스 갤러리에만 저장. 서버 업로드/조회 API 없음(results 등 응답에 사진 필드 없음).

**러닝 중 - 다른 유저**

- 유저 트랙: 다른 참가자 진행 상황을 트랙 형태로 표시.
- 유저 비교 정보: 앞뒤 참가자 거리·페이스 정보.

**러닝 후 - 대시보드**

- 본인 경로 확인: 달린 경로 지도 표시.
- 참가자 공통 정보: 참가자별 러닝 시간·이동 거리.
- 참가자 상세 정보: 평균 페이스·소모 칼로리·평균 케이던스·고도·구간별 평균 페이스. 같은 방 참가자끼리는 서로의 상세 기록 열람 가능(본인 제한 없음). GPS 트랙 저장 방식은 2번 도메인 제약 참고.

### 기록·대회

**기록**

- 일정 조회: 캘린더로 등록된 대회 일정과 러닝 기록 확인. 러닝 기록 API와 북마크 대회 API를 분리 제공 — 클라이언트가 두 응답을 날짜 기준으로 병합. 통합 캘린더 API로 합치지 않음, 각 API는 다른 화면에서도 재사용.
- 일정 상세 정보: 선택 일정의 경로·러닝 기록·등록 일정 확인.

**대회**

- 검색 기능: 대회명으로 검색.
- 필터 기능: 날짜·지역·거리 조건 필터.
- 대회 목록: 대회명·지역·장소·개최 일정·거리·접수 기간·링크 제공.
- 대회 상세 정보: 선택 대회 공식 홈페이지로 이동.
- 일정 추가: 참가 신청이 아니라 관심 대회를 내 캘린더(기록 화면의 일정 조회)에 북마크하는 동작. 대회는 마라톤 일정 정보(외부 정보)로 러닝 기록(`running_records`)과 무관 — 참가 확정 등 별도 상태값 불필요, `user_running_contests`는 단순 연결 테이블로 충분.

### 피드

**피드 목록 페이지**

- 무한 스크롤 조회, 목록에서 바로 이미지/전체글/좋아요·댓글 버튼 노출.
- 좋아요 버튼: 목록에서 바로 토글.
- 댓글 버튼 → 댓글 모달로 이동.
- 팔로잉 탭: 팔로우한 사용자 게시물 최신순. 공개 범위 필터: 팔로우 유저의 "팔로워"/"전체" 공개 피드 + 내 피드 전부.
- 전체 탭: 팔로잉 + 타 사용자 게시물 혼합(인스타 탐색 방식). 초기 버전은 최신순 + 가벼운 가중치(친구의 친구 등), 개인화 추천은 이후 확장. 공개 범위 필터: "전체" 공개 피드 + 팔로우 유저의 "팔로워" 공개 피드.
- 프로필 클릭 → 프로필로 이동.
- 통합 검색: 계정/게시글 검색. 게시글 노출 범위: "전체" 공개 피드 + 본인 피드만.
- 댓글: 작성/수정/삭제, 답글 작성/수정/삭제, 댓글 좋아요.
  - 수정은 작성자 본인만 가능(피드 소유자는 삭제만). 수정 시 이전 내용은 `delete_comments`에 스냅샷 보관(피드와 동일).
  - 대댓글 depth는 1단계 제한 — `comments.parent_comment_id`가 있는 댓글엔 답글 작성 API 자체를 차단.
  - 답글은 목록에 바로 포함하지 않고 "답글 N개 보기" 탭 시 지연 로딩(인스타그램 방식). 댓글 목록 정렬은 등록순.
  - 삭제 권한: 댓글 작성자 본인 또는 그 댓글이 달린 피드의 소유자(악플/스팸 관리 목적).

**피드 작성 페이지**

- 이미지 업로드: 여러 장 선택해 첨부.
- 글 작성: 텍스트/이미지 둘 다 비운 채 게시 불가 — 최소 하나 필수.
- 노출 범위 선택: 팔로워/전체/나만.
- 위치/경로 첨부: 별도 기능 아님 — 러닝 기록 템플릿 선택에 통합. 템플릿 카드에 경로 미리보기 지도 포함 — 피드 카드 `record.routePolyline`은 `running_records.route_polyline`의 다운샘플 encoded polyline(별도 API 없음).
- 러닝 기록 템플릿 선택: 과거 기록 선택 시 거리/시간/페이스·경로 미리보기가 카드로 자동 삽입. 대시보드에서 진입한 경우 방금 완료한 기록이 기본 선택.
- 게시 버튼: 등록 후 피드 목록 최상단에 노출.

### 프로필·설정

**프로필 페이지**

- 프로필 요약: 사진, 닉네임, 총 누적고도, 마일리지, 소개글, 뱃지, 잔디, 팔로워/팔로잉 수 표시.
  - 마일리지: 별도 저장 테이블 없이 `running_records.total_distance` 합산 — 누적은 전체, 월별은 해당 월 기록 합산.
  - 총 누적고도: `running_records.elevation_gain` 전체 합산 — nullable 컬럼이라 null 기록은 합산 제외.
- 내가 올린 피드 그리드: 프로필 하단에 본인 작성 피드를 썸네일 그리드로 표시.
- 피드 작성 버튼: 누르면 피드 작성 페이지로 이동.
- 피드 편집: 게시글 수정/삭제, 노출 범위 설정.
- 뱃지·잔디 더보기: 같은 페이지 내에서 전체 뱃지 목록·월별 잔디 상세 확장(별도 페이지 이동 없음).
  - 잔디는 일 단위가 아니라 주 단위 — 해당 주 `running_records` 개수로 진하기 표시. 1회=가장 연함 ~ 7회=가장 진함, 8회 이상은 7회와 동일한 최고 진하기로 캡.
  - API는 주별 러닝 횟수만 반환(예: `{week, count}`), 색상 단계 매핑은 프론트에서 처리.
- 팔로워/팔로잉 수 클릭 → 팔로워/팔로잉 목록 페이지로 이동.
- 편집 버튼 → 프로필 편집 페이지로 이동.
- 설정 버튼 → 설정 페이지로 이동.
- 조건부 표시: 본인 프로필이면 편집/설정 버튼, 타인 프로필이면 팔로우 버튼.
- 비공개 정보 처리: 1차는 프로필 전부 공개.
  - **[2차] 지인 마스킹**(`users.profile_visibility=FRIENDS`): 맞팔 아닌 타인에겐 프로필 요약의 누적고도·마일리지를 null + `masked=true`로 마스킹(정상 200), 뱃지·잔디·팔로워/팔로잉 목록 조회는 `403 PROFILE_PRIVATE`. 사진·닉네임·소개글·팔로워/팔로잉 수는 항상 공개.
- 팔로우 버튼: 누르면 팔로우/언팔로우.
- 신고/차단 메뉴: 1차 범위 제외.

**프로필 편집 페이지**

- 사진 변경, 닉네임 변경(서비스 전반 표시 갱신), 소개글 변경. `nickname`은 `user_onboardings`에 저장 — 닉네임 변경은 별도 API 없이 `PATCH /users/me`에 통합, 서버가 `user_onboardings.nickname` 갱신, 중복 시 `409 NICKNAME_ALREADY_EXISTS`

**팔로워/팔로잉 목록 페이지**

- 탭 전환으로 팔로워/팔로잉 목록 조회.
- 검색: 이름 기준 필터링.
- 팔로우/언팔로우 버튼: 즉시 상태 토글.
- 사용자 클릭 → 해당 사용자 프로필로 이동.

**설정 페이지**

- 알림 설정: 전체 알림 on/off 단일 토글(`users.alert_consent` — 좋아요·댓글·팔로우·매칭·리마인더·대회 푸시 전부 관장). 종류별 개별 토글은 1차 미포함.
- 프로필/피드 공개 범위 설정: **[2차]** `profile_visibility`(지인 마스킹 on/off)·`feed_default_visibility`(피드 기본 공개 범위) 추가 예정. 1차는 피드 작성 시 공개 범위(`feeds.visibility`)를 매 피드 개별 선택, 기본 선택값은 클라가 PUBLIC 고정.
- 로그아웃/회원탈퇴: 확인 팝업 후 처리.
  - 로그아웃: `POST /auth/logout`(바디 없음 — 서버가 요청 토큰으로 본인 식별). 해당 access 토큰을 서버 블랙리스트에 올려 즉시 무효화 — 만료 전이라도 그 토큰 요청은 `401 TOKEN_BLOCKED`
  - 기기 단위 푸시 중단(`deviceId`·`is_active`)은 2차 — deviceId 도입 시.

### 공통 (특정 화면 소속 아님)

**알림 (푸시)**

- 매칭 확정/실패, 세션 시작 리마인더, 새 팔로워, 피드 좋아요/댓글, 대회 접수 시작 — 알림 종류별로 눌렀을 때 최적 화면으로 랜딩. "대회 접수 시작"은 전체 대회가 아니라 `user_running_contests`로 북마크한 대회에 한해서만 발송.
- 인앱 알림함(목록 조회 화면) 없음 — 푸시로만 발송, 클릭 시 랜딩만 수행. `notification` 테이블/조회 API 불필요, 디바이스 등록(`user_devices`) API만 유지.

## 2. 도메인 제약

> 전체 테이블·컬럼·타입·PK 규칙·enum·단위는 `erd.md`가 단일 출처. 여기서는 API 작성·구현에 필요한 도메인 제약과 설계 맥락만 남긴다.

**API 리소스 네이밍**: DB 테이블명과 API 용어를 "room"으로 통일한다 — 식별자는 `runningRoomId`(Long, = `running_rooms.running_room_id`), URL은 `/running-rooms/...` 계열. `api-spec.md` 5~6번이 아직 "session" 용어(`runningSessionId`, `/running-sessions/...`)로 작성되어 있어 정리가 필요하다.

**방은 항상 존재한다**: 솔로 러닝도 1인 방(`running_rooms.type='SOLO'`)과 `running_players`·`running_room_sessions` row를 만든다. 방 참가자 조회·기록 저장 경로가 매칭과 솔로에서 완전히 같아지고, `running_records.running_room_id`가 NOT NULL이 된다.

**매칭·러닝 설계**: 매칭은 REST가 아니라 WebSocket(`/ws/running-matches`)으로 처리한다.

- 매칭 시작 = 방 배정 — 조건이 맞는 모집 중인 방에 합류하거나, 없으면 1인 방을 새로 만든다(`type='MATCH'`, `status='MATCHING'`). 대기 상태·참가자·성사/취소·시작·러닝 진행·종료까지 WS 메시지로 처리(전문은 `api-spec.md` 5번).
- 방 `type`은 생성 시 고정된다 — 매칭 후보 스캔·대기 인원 집계가 `type='MATCH'`만 보므로 솔로 방·초대방이 후보에 섞이지 않는다.
- 러닝 시작은 클라 주도(`RUNNING_START` C→S, 카운트다운은 클라 자체 시계). 메시지 네이밍 규칙: 클라 발신 현재형 / 서버 발신 과거형.
- 러닝 중 다른 참가자 위치·진행상황 공유도 WS로 처리 — 러닝방 입장 시 연결, 위치를 주기적으로 발신/수신. 연결 상태와 끊김 횟수는 `running_room_sessions.is_connected`·`leave_count`에 기록하고 페널티 판정 근거로 쓴다.
- 매칭 성사 후에는 `runningRoomId`(Long, = `running_rooms.running_room_id`)로 REST 호출(결과 조회) — 그 외 REST 매칭 엔드포인트는 없음.

**`user_devices.is_active`**: 로그인 시 디바이스 등록/갱신 API가 `is_active=true`로 전환(푸시 준비). 기기 단위 비활성화(로그아웃 시 false)는 deviceId 도입 시(2차) — 1차 로그아웃은 토큰 블랙리스트만(deviceId 안 받음).

**이미지 업로드**(`feed_images`, `users.profile_image_key` 등 전체 공통): Presigned URL 방식 — 클라이언트가 업로드용 presigned URL 요청 → 서버가 S3 presigned URL 발급 → 클라이언트가 S3에 직접 업로드 → 반환받은 key를 본 API(피드 작성, 프로필 사진 변경 등) 요청에 포함해 전달.

**GPS 트랙**: 원본 트랙은 Postgres 테이블이 아님 — `running_records.gps_track_key`로 S3 객체를 참조. **매칭·솔로 모두 서버가 저장한다.**

- 러닝 중엔 Redis(`running_room_id + user_id` 키)에 좌표를 버퍼링하고, 종료 시 서버가 S3에 업로드한다. 클라이언트는 업로드하지 않고 종료 신호(WS `RUNNING_FINISH`)만 보내며, 이 시점에 서버가 `running_records`·`running_splits`를 일괄 INSERT한다.
- **솔로 러닝**(`type='SOLO'`, 혼자 뛰지만 방·플레이어 row는 만든다)도 같은 경로를 탄다 — 클라 presigned 업로드나 기록 저장 REST API를 쓰지 않는다. ⚠️ `api-spec.md` 7-3·7-4(솔로 전용 presigned URL·기록 저장)는 이 결정으로 무효가 되므로 정리가 필요하다.
- **경로 데이터의 용도 분리**: `gps_track_key`(S3 원본)는 재계산·분석 전용으로 **API 응답에 쓰지 않는다**. 클라이언트에 내려주는 경로는 전부 `route_polyline`(encoded polyline, precision 5) 하나뿐이며, 조회 한 번에 딸려 나와 S3 왕복이 없다.

**피드-러닝 기록 연결**: `feeds.running_record_id`는 `running_records` 참조 — 피드 작성 시 "러닝 기록 템플릿 선택"에 대응. nullable — 러닝 기록 없이 글+사진만으로도 작성 가능.

**피드 공개 범위**: `feeds.visibility` 값은 3종(팔로워/전체/나만) — "나만"은 본인 피드 목록/프로필 그리드 조회 시에만 노출, 팔로워 피드·전체 피드 조회에서는 제외.

**뱃지**: 획득 조건/기준을 저장하는 테이블은 ERD에 없음 — 서버 로직으로 자동 판정·지급. 지급 트리거 API 없음, 조회 API만 존재.

**날씨**: 홈 화면 날씨는 클라이언트가 키리스 무료 날씨 API(예: Open-Meteo)를 직접 호출 — 서버 API 없음. 유료 제공자·캐싱 필요 시점에 프록시 추가 재검토.

**`running_rooms.status`**: MATCHING/MATCHED/STARTED/FINISHED/CANCELLED. 솔로 방은 `STARTED`로 생성돼 `MATCHING`·`MATCHED`를 거치지 않는다 — 상태 전이 규칙은 `type`과 함께 판정해야 한다. 매칭 취소 시 `status='CANCELLED'`만 사용 — 취소 이력도 조회 가능해야 하므로 목록에서 제외하지 않음. `deleted_at`은 별도 용도(예: 관리자의 부정 방 숨김 처리)로 남겨두고 이번 명세에서는 다루지 않는다.

**`STARTED`→`FINISHED` 전환**: 둘 중 먼저 오는 시점에 자동 전환 — ① `RUNNING` 참가자 전원이 기록 제출 완료(`COMPLETED`) 시 즉시 `FINISHED`, ② 미제출자가 있어도 시작 후 서버 설정 시간 경과 시(타임아웃) 스케줄러가 `FINISHED` 처리. 타임아웃 값은 운영 정책.

**`running_players.status`**: 값 목록과 의미는 `erd.md` §6이 단일 출처. 요점만 — 페널티 여부는 이탈 시점에 서버가 판정해 `*_LEFT_PENALTY`/`*_LEFT_NO_PENALTY`로 고정 저장하므로 별도 페널티 테이블이 없다. 대기 취소·초대 거절은 별도 상태값 없이 `deleted_at`만 찍고 `running_room_sessions` 링크를 삭제한다(이탈과 달리 방 이력을 남기지 않음). `INVITED`는 친구 초대(`type='INVITE'`)용 — MVP 미사용.

**목표 거리 vs 실제 거리**: 목표는 `target_*`, 실적은 `total_*`로 이름이 갈린다 — `running_players.target_distance`·`running_rooms.target_distance`는 설정한 목표 거리, `running_records.total_distance`는 러닝 종료 후 확정된 실제 이동 거리. 서로 다른 값이다.

**날씨 기록**: `running_records.weather_code`(WMO 4677 원본값)·`temperature`만 저장하고 "악조건 여부" 같은 판정 결과는 저장하지 않는다 — 기준이 바뀌어도 과거 기록을 다시 계산할 수 있어야 하기 때문.

**회원탈퇴 시 연관 데이터 처리** (테이블별):

- **유지**: `feeds`, `comments`, `running_records`(+`running_splits`) — 같은 방 참가자의 대시보드 기록 비교가 서비스 핵심이라 탈퇴해도 기록은 유지. 해당 테이블들의 `user_id` FK는 하드delete 이후에도 값이 남아야 하므로 DB 레벨 CASCADE 걸지 않고 애플리케이션 레벨에서 처리. 작성자가 탈퇴한 경우 응답의 작성자 정보는 `{ userId, nickname: "탈퇴한 사용자", profileImageUrl: null, isDeleted: true }`로 대체(고정 문구 — 실제 닉네임은 스냅샷 안 하므로 조회하지 않음).
- **유지 (카운트 재계산 안 함)**: `feed_likes`, `comment_likes` — 탈퇴자가 누른 좋아요는 남겨두고 `like_count` 그대로(인스타그램 방식).
- **즉시 삭제**: `follows`(`ON DELETE CASCADE` — 팔로워/팔로잉 목록에서 탈퇴 유저 노출 방지), `user_follow_stats`(탈퇴자 본인 row), 개인 데이터 테이블 전부 — `user_onboardings`, `user_devices`, `oauth_users`, `user_badges`, `user_running_contests`. `running_players`는 `user_id`가 논리 참조라 DB CASCADE가 걸리지 않으므로 **앱이 탈퇴 트랜잭션에서 명시적으로 DELETE**한다(연결 테이블 `running_room_sessions`은 `running_player_id` FK `ON DELETE CASCADE`로 연쇄)
- `follows` CASCADE 삭제로 어긋나는 상대방들의 `user_follow_stats` follower/following_count는 탈퇴 트랜잭션에서 즉시 재계산(감소 반영) — `follows`는 row가 삭제돼 목록과 수가 일치해야 하기 때문(`feed_likes`는 row 유지라 카운트 유지 — 기준이 다름).

**삭제 처리 방식** (리소스별로 다름 — API 설계 시 각각 구분해서 반영):

- **`users`/`badges`**: 하드delete + 아카이브 — 실제 DELETE 전에 `delete_users`/`delete_badges`에 스냅샷 먼저 저장(감사/로그 용도, 복구 기능 없음).
- **`feeds`**: `deleted_at` 소프트delete(복구 가능·조회 제외 처리).
- **`comments`**: 답글 유무로 분기(레딧 방식, 두 경우 모두 `delete_comments` 스냅샷 먼저 저장) — 답글 없으면 하드delete, 답글 있으면 톰스톤(row 유지 + 내용 비움 + `deleted_at` 기록, "삭제된 댓글입니다" 자리표시로 노출하고 답글 스레드 유지).
