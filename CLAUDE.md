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

- `docs/architecture.md` — 헥사고날 레이어 규칙·구현 스타일 (코드 작성 전 필독)
- `docs/code-convention.md` — 네이밍·API 표면·PR 규칙 / `docs/commit-convention.md` — 커밋·브랜치 규칙
- `docs/api-spec-v1.md` — API 명세 / `docs/erd.md` — DB 스키마 / `docs/api-spec-context.md` — 기능명세·기획 맥락

저장소 문서가 정본이고 노션은 게시본이다. 기능명세는 자주 바뀌니 구현 전 스펙을 다시 읽는다. 스펙 수정 시 `docs/api-spec-prompt.md`의 컨벤션을 따르고 저장소 문서 → 노션 → 지라(TMB) 순으로 동기화한다.

## 주의사항

- `BusinessException`/`ErrorCode`가 application·domain 양쪽 `common/exception/`에 중복 존재 — 참조 시 어느 쪽인지 확인.
- `.env` 등 시크릿 파일은 절대 커밋하지 않고, 키 값은 출력 시 마스킹한다.
