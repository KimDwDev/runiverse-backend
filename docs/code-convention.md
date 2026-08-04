# Code Convention

## 기본 원칙

- Java 코드는 Google Java Style을 따른다.
- 포맷팅은 루트 `.editorconfig`를 따른다.
- 커밋 및 브랜치 규칙은 [commit-convention.md](commit-convention.md)를 따른다.
- API 표면(엔드포인트·DTO 필드) 규칙은 [api-convention.md](api-convention.md)를 따른다.
- 기존 코드와 컨벤션이 충돌하지 않는 범위에서 주변 코드의 구현 스타일을 유지한다.

## 네이밍

- 클래스·인터페이스·enum·record: `PascalCase`
- 메서드·변수·파라미터: `camelCase`
- 상수: `UPPER_SNAKE_CASE`
- 패키지: 소문자 — 단어 구분이 필요하면 계층으로 분리, 베이스 패키지 `running_service`만 언더스코어 유지(리네임하지 않는다)
- 이름은 동작·역할이 드러나도록 짓고, 축약어·의미 없는 이름(`data`, `info`, `temp`)은 피한다.

## PR

- 기본 브랜치는 `dev`이며, `.github/pull_request_template.md` 양식을 채워 작성한다.
