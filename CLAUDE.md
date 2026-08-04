# CLAUDE.md

Runiverse 백엔드 — 원격 동반 러닝 플랫폼의 API 서버.

## 빌드 및 테스트

```bash
cd running-service
./gradlew test                                  # 전체 테스트
./gradlew test --tests '*JwtTokenAdapterTest'   # 단일 클래스 (패턴 매칭)
./gradlew bootRun                               # 앱 실행 (컨텍스트 경로: /api/v1)
```

- 테스트·실행에는 `.env` 필수(spring-dotenv) — 없으면 통합 컨텍스트 로드 자체가 실패한다.
- 테스트는 Mockito javaagent로 실행된다(build.gradle의 `mockitoAgent`) — 테스트 JVM 옵션 수정 시 주의.

## 문서 인덱스 — 구현 전 반드시 해당 문서를 읽을 것

- `docs/architecture.md` — 클린 아키텍처 + DDD 레이어 규칙·구현 스타일 (코드 작성 전 필독)
- `docs/code-convention.md` — 네이밍·테스트·PR 규칙 / `docs/commit-convention.md` — 커밋·브랜치 규칙 / `docs/api-convention.md` — API 표면 규칙 (에러 포맷·페이지네이션·인증·단위 접미사)
- `docs/api-spec.md` — API 명세 / `docs/erd.md` — DB 스키마 / `docs/feature-spec.md` — 기능 명세·도메인 제약

스펙 문서는 초안이며 자주 바뀐다 — 구현 전 다시 읽고, 코드와 어긋나면 사용자에게 확인한다.

## 주의사항

- `BusinessException`/`ErrorCode`가 application·domain 양쪽 `common/exception/`에 중복 존재 — 참조 시 어느 쪽인지 확인.
- `.env` 등 시크릿 파일은 절대 커밋하지 않고, 키 값은 출력 시 마스킹한다.
