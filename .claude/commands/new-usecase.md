---
description: 클린 아키텍처 패턴에 맞는 새 유스케이스 스캐폴딩 (인자 - <도메인> <기능명>)
argument-hint: <도메인> <기능명> (예 - auth withdraw)
---

새 유스케이스를 이 프로젝트의 클린 아키텍처 패턴에 맞게 스캐폴딩해줘: $ARGUMENTS

시작 전에 반드시:

1. `docs/api-spec.md`에서 해당 기능의 API 명세(요청/응답 필드, 에러 케이스)를 찾아 읽는다.
2. `docs/erd.md`에서 관련 테이블·컬럼·enum을 확인한다.
3. 기존 구현 예시로 `application/auth/command/signup`과 `login` 패키지를 참고해 동일한 스타일로 작성한다.

생성할 구성 (패키지 루트: `com.runiverse.running_service`):

- `application/<도메인>/command/<기능>/` — `<기능>Command`(입력) / `<기능>Handler`(`port/in` 구현) / `<기능>Result`(출력)
- `application/<도메인>/port/in/<기능>Usecase` — 인바운드 포트 인터페이스
- `application/<도메인>/port/out/` — 필요한 아웃바운드 포트. 기존 포트에 메서드를 추가하지 말고 단일 메서드 포트를 새로 만든다 (기존 포트로 충분하면 재사용)
- `presentation/<도메인>/controller/` — 컨트롤러 엔드포인트 + `request/`·`response/` DTO (필드명은 camelCase + 단위 접미사)
- 도메인 규칙이 필요하면 `domain/` 애그리거트·VO에 추가 (프레임워크 의존 금지, VO 생성 시점 검증)
- infrastructure 어댑터가 필요하면 기존 어댑터(`UserPersistenceAdapter` 등) 스타일을 따른다

완료 후 도메인·기능 단위 테스트를 기존 테스트 스타일로 작성하고 `./gradlew test`로 확인한다.
