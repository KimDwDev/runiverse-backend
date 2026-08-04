# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

Runiverse 백엔드 — 원격 동반 러닝 플랫폼. Spring Boot 4.1.0 / Java 21, 단일 모듈 `running-service`, PostgreSQL + Redis. 현재 인증(Auth) 도메인만 구현되어 있다.

## 빌드 및 테스트

소스는 `running-service/` 모듈 안에 있다. Gradle 명령은 그 안에서 실행한다.

```bash
cd running-service
./gradlew test                                  # 전체 테스트
./gradlew test --tests '*JwtTokenAdapterTest'   # 단일 클래스 (패턴 매칭)
./gradlew bootRun                               # 앱 실행 (컨텍스트 경로: /api/v1)
```

- 환경변수는 `.env`로 주입(spring-dotenv). DB·Redis·JWT·카카오 키가 전부 필수라 `.env` 없이는 통합 컨텍스트가 뜨지 않는다.
- 테스트는 Mockito javaagent로 실행된다(build.gradle의 `mockitoAgent`) — 테스트 JVM 옵션 수정 시 주의.

## 스펙 문서 (단일 출처)

- `docs/api-spec-v1.md` — API 명세 정본 (REST + WebSocket)
- `docs/erd.md` — DB 스키마 정본 (테이블·enum·인덱스·단위)
- `docs/api-spec-context.md` — 기능명세·도메인 제약·기획 맥락

저장소 문서가 정본이고 노션은 게시본이다. **기능 구현 전 반드시 해당 기능의 스펙을 다시 읽을 것** — 기능명세는 자주 바뀐다. 스펙 문서 수정 시에는 `docs/api-spec-prompt.md`의 컨벤션을 따르고 저장소 문서 → 노션 → 지라(TMB) 순으로 동기화한다.

## 아키텍처

헥사고날 + DDD. 패키지 루트 `com.runiverse.running_service`, 의존 방향은 항상 안쪽(domain)으로:

```
presentation → application(port/in) → domain
infrastructure → application(port/out) → domain
```

- **domain/**: 프레임워크 의존 금지. VO 생성 시점 검증 + 도메인 예외.
- **application/**: 기능별 패키지에 `Command`/`Handler`/`Result` 3종 세트, `port/in`의 `*Usecase` 구현.
- **port/out**: 단일 메서드 인터페이스로 잘게 쪼갠다 — 기존 포트에 메서드 추가 대신 새 포트 생성. Handler는 필요한 포트만 주입.
- **infrastructure/**: 아웃바운드 어댑터. 도메인 ↔ JPA 엔티티 변환은 어댑터 책임.
- **presentation/**: 컨트롤러 + DTO. 예외는 `GlobalExceptionHandler`에서 일괄 변환.

기존 구현 스타일 참고는 `application/auth/command/signup`·`login` 패키지가 기준.

## 컨벤션

포맷팅은 루트 `.editorconfig`가 담당한다(4칸 들여쓰기, 100자 제한, K&R 중괄호, 와일드카드 import 금지). 그 외는 Google Java Style을 따르되, 아래 팀 규칙이 우선한다. 규칙끼리 충돌하거나 불가피하게 벗어나야 하면 임의로 정하지 말고 사용자에게 보고 후 반영한다.

- **네이밍**: 메서드·변수 camelCase(메서드는 동사) / 클래스 PascalCase / 상수 UPPER_SNAKE_CASE / 패키지 flatcase. 베이스 패키지 `running_service`만 예외 — 리네임하지 말 것.
- **API 표면**: 필드명 camelCase + 단위 접미사(`...Meters`, `...Seconds` 등), `userId`만 UUID(그 외 ID는 Long). DB는 snake_case.
- **커밋**: `docs/commit-convention.md` — `<이모지> <Type>: <설명>` (예: `📍 Feat: 카카오 로그인 기능 추가`). 하나의 커밋에 하나의 논리적 변경만.
- **브랜치**: `<type>/<domain>` kebab-case (예: `feat/oauth-login`). 기본 브랜치 `dev`. PR은 `.github/pull_request_template.md` 양식.

## 주의사항

- `BusinessException`/`ErrorCode`가 application·domain 양쪽 `common/exception/`에 중복 존재 — 참조 시 어느 쪽인지 확인.
- `.env` 등 시크릿 파일은 절대 커밋하지 않고, 키 값은 출력 시 마스킹한다.
