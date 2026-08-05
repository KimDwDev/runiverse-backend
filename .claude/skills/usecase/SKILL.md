---
name: usecase
description: >-
  Runiverse 백엔드에 명세된 API 엔드포인트·유스케이스·도메인 동작을 구현하거나 확장할 때 사용한다.
  api-spec.md를 도메인, 애플리케이션, JPA 어댑터, 컨트롤러, 테스트로 옮기고 에러를 처리한다.
  "API 만들어줘", "기능 구현해줘", "스펙 N번 구현", "엔드포인트 추가"가 해당한다. 자바 코드를
  바꾸지 않는 요청(문서·명세 작성, 정합성 점검만)과 로그·주석 추가, 리네임에는 사용하지 않는다.
---

# 유스케이스 구현

명세 한 건을 테스트 가능한 코드로 구현한다. 레이어와 네이밍은 `docs/architecture.md`를 따른다.

## 저장소 고유 규칙

- 같은 도메인의 여러 단일 메서드 포트는 하나의 어댑터가 구현한다. 어댑터를 불필요하게 나누지 않는다.
- **Spring Data Repository를 쓰지 않는다.** `EntityManager`와 JPQL 텍스트 블록을 사용한다.

## 사전 확인

`api-spec.md` 색인에서 대상 번호나 경로를 특정한다. 대상이 모호하거나 계약이 빠졌으면 수정 전에 한 번에 확인한다. 다음 문서에서 해당 기능의 절만 읽는다.

| 문서 | 확인할 것 |
|---|---|
| `api-spec.md` | 경로·필드·상태 코드·에러 케이스·**검증 메시지 문구** |
| `erd.md` | 컬럼·타입, §0 PK/FK 정책, §6 enum 사전 |
| `feature-spec.md` | 해당 화면 절(§1)과 공통 도메인 제약(§2) |
| `api-convention.md` | 단위 접미사·커서 페이지네이션·토글 액션 |

**문서끼리 또는 명세와 코드가 어긋나면 멈추고 차이와 영향을 사용자에게 확인한다.** 이미 받은 수정 기준은 재확인하지 않는다. 명세는 초안이므로 코드가 맞을 수 있다.

정합성 점검만 요청받으면 `spec-check`를 사용한다. 점검과 구현을 함께 요청받으면 차이와 수정 기준을 먼저 합의한다.

형태는 `application/auth/command/signup`(생성)·`login`(조회+토큰)과 맞춘다.

## 구현

컴파일을 유지하며 안쪽에서 바깥쪽으로 구현한다. 변경할 레이어만 `references/layer-patterns.md`에서 읽는다.

**1) 도메인** — 값 규칙이나 상태 전이가 있을 때만 변경한다. 프레임워크를 import하지 않는다.

**2) 애플리케이션** — `command/<기능>/`에 `Command`·`Handler`·`Result`, `port/in/<기능>Usecase`, 필요한 단일 메서드 `port/out/`을 만든다. 출력 포트명은 동사로 시작한다. 유스케이스 거부 조건은 `application/<도메인>/exception/`에 둔다.

**3) 에러 처리** — 애플리케이션 예외를 추가하거나 요청 값 규칙을 바꿀 때 `references/error-registration.md`를 따르고 DTO·VO 검증을 함께 반영한다.

**4) 인프라** — JPA 엔티티는 `erd.md`의 제약을 그대로 반영한다. 도메인 ↔ 엔티티 변환은 어댑터가 맡고, 기존 도메인 어댑터가 있으면 `implements`만 추가한다.

**5) 프레젠테이션** — 설정이 붙이므로 `@RequestMapping`에 `/api/v1`을 넣지 않는다. DTO 단위 접미사는 풀네임(`...Meters`·`...SecondsPerKm`·`...Kg`·`...Cm`, `...Spm`만 약어)으로 쓰고, Bean Validation 메시지는 **`api-spec.md` 문구 그대로** 둔다.

**6) 테스트** — `docs/code-convention.md`와 `UserVoTest`·`UserOnboardTest`·`UserPersistenceAdapterTest`를 따른다. 도메인 예외의 타입·메시지, 핸들러의 성공·실패 경로와 실패 후 중단을 검증한다.

테스트 제외 요청이 있으면 새 테스트를 만들지 않는다. 컴파일과 기존 테스트로 검증하고 미완료 커버리지를 보고한다.

## 검증

```bash
cd running-service && ./gradlew test
```

`.env`가 없어 통합 컨텍스트 로드가 실패하면 `compileJava compileTestJava`와 실행 가능한 관련 단위 테스트를 돌리고, 생략한 검증을 밝힌다.

응답 필드명·상태 코드·에러 코드, 포트의 단일 메서드 여부, 새 ErrorCode의 `EXPOSED_CODES` 등록을 확인한다.

구현과 필요한 테스트를 작성하고 위 검증을 마쳐야 완료다. 구현 요약, 검증 결과, 남은 결정이나 실행하지 못한 항목만 보고한다.

## WebSocket은 예외

`api-spec.md` 5번의 WebSocket 메시지는 구현 선례가 없다. 세션 상태·ack·Redis 버퍼링 키를 첫 구현에서 정하게 되므로, REST 절차를 적용하지 말고 설계를 먼저 합의한다.
