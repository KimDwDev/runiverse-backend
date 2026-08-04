# Code Convention

포맷팅은 루트 `.editorconfig`, 커밋·브랜치는 [commit-convention.md](commit-convention.md)를 따른다. 그 외는 Google Java Style이되 아래 팀 규칙이 우선한다.

## 네이밍

- 메서드·변수 camelCase (메서드는 동사 시작) / 클래스 PascalCase / 상수 UPPER_SNAKE_CASE / 패키지 flatcase
- 베이스 패키지 `running_service`만 예외 — 리네임하지 말 것

## API 표면

- 필드명 camelCase + 단위 접미사 (`...Meters`, `...Seconds` 등)
- ID 타입: `userId`만 UUID, 그 외 ID는 Long
- DB 컬럼은 snake_case

## PR

- 기본 브랜치 `dev`, `.github/pull_request_template.md` 양식을 채운다
