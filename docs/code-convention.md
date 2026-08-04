# Code Convention

## 기본 원칙

- Java 코드는 Google Java Style을 따른다.
- 포맷팅은 루트 `.editorconfig`를 따른다.
- 커밋·브랜치·PR 규칙은 [commit-convention.md](commit-convention.md)를 따른다.
- API 표면(엔드포인트·DTO 필드) 규칙은 [api-convention.md](api-convention.md)를 따른다.
- 이 문서의 규칙을 우선하며, 규칙에 정의되지 않은 사항은 주변 코드의 구현 스타일을 따른다.

## 네이밍

- 클래스·인터페이스·enum·record: `PascalCase`
- 메서드·변수·파라미터: `camelCase`
- 상수: `UPPER_SNAKE_CASE`
- 패키지: 소문자 — 단어 구분이 필요하면 계층으로 분리, 베이스 패키지 `running_service`만 언더스코어 유지(리네임하지 않는다)
- 이름은 동작·역할이 드러나도록 짓고, 축약어·의미 없는 이름(`data`, `info`, `temp`)은 피한다.

## 테스트

- 새로운 비즈니스 로직에는 단위 테스트를 작성한다.
- 버그 수정 시 실패를 재현하는 테스트를 먼저 추가한다.
- given-when-then 구조를 따르고, 구분 주석은 `// given`, `// when`, `// then`으로 통일한다. 실행과 검증이 한 문장인 경우(예외 발생 검증 등)는 `// when & then`으로 합칠 수 있고, 토큰 뒤에 `-> 설명`으로 의도를 덧붙일 수 있다.
- 테스트 메서드 이름과 `@DisplayName`은 행위가 드러나도록 작성한다.
- 내부 구현보다 외부에서 관찰 가능한 결과를 검증한다.
- 불필요한 mock과 과도한 `verify()`를 사용하지 않는다.
