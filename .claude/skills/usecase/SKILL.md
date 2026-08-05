---
name: usecase
description: >-
  Runiverse 백엔드에 API 엔드포인트나 유스케이스를 구현할 때 사용한다. api-spec.md를 도메인,
  애플리케이션, JPA 어댑터, 컨트롤러, 테스트로 옮기는 절차와 에러 코드 등록 규칙을 다룬다.
  "API 만들어줘", "기능 구현해줘", "스펙 N번 구현", "엔드포인트 추가" 요청에 적용한다.
---

# 유스케이스 구현

명세 한 건을 테스트 가능한 코드로 구현한다. 레이어와 네이밍은 `docs/architecture.md`를 따르고, 여기에는 저장소 고유 함정만 기록한다.

## 저장소 고유 규칙

- 하나의 어댑터가 여러 단일 메서드 포트를 구현한다. 포트가 늘어나더라도 같은 도메인의 어댑터를 불필요하게 나누지 않는다.
- **Spring Data Repository를 쓰지 않는다.** `EntityManager` + JPQL 텍스트 블록. `JpaRepository` 도입은 의존성 추가라 요청 없이 하지 않는다.

## 사전 확인

문서 전체가 아니라 해당 기능의 절만 찾아 읽는다.

| 문서 | 확인할 것 |
|---|---|
| `api-spec.md` | 경로·필드·상태 코드·에러 케이스·**검증 메시지 문구** |
| `erd.md` | 컬럼·타입, §0 PK/FK 정책, §6 enum 사전 |
| `feature-spec.md` | 해당 화면 절(§1)과 공통 도메인 제약(§2) |
| `api-convention.md` | 단위 접미사·커서 페이지네이션·토글 액션 |

**명세와 코드가 어긋나면 멈추고 사용자에게 확인한다.** 명세는 초안이라 코드 쪽이 맞을 수 있다.

만들 것과 비슷한 기존 유스케이스를 열어 형태를 맞춘다. 기준은 `application/auth/command/signup`(생성)·`login`(조회+토큰).

## 구현

매 단계 컴파일이 유지되도록 안쪽에서 바깥쪽으로 구현한다. 세부 패턴은 필요할 때만 `references/layer-patterns.md`를 읽는다.

**1) 도메인** — 값 규칙이나 상태 전이가 있을 때만. VO는 `record` + 컴팩트 생성자 검증, 규칙마다 전용 예외 하나, 코드는 `domain/common/exception/ErrorCode`에. 프레임워크를 import하지 않는다.

**2) 애플리케이션** — `command/<기능>/`에 `Command`/`Handler`/`Result`, `port/in/<기능>Usecase`, 필요한 `port/out/`(단일 메서드·동사 시작). 유스케이스가 거부하는 조건은 `application/<도메인>/exception/`에 둔다.

**3) 에러 처리** — 애플리케이션 예외를 추가하거나 요청 값 규칙을 변경할 때 `references/error-registration.md`를 읽고 에러 코드 등록과 DTO·VO 검증을 함께 반영한다.

**4) 인프라** — JPA 엔티티는 `erd.md` 표 그대로(제약·`@Check`·`@UniqueConstraint`), setter 없이 static `create(...)`. 도메인 ↔ 엔티티 변환은 어댑터 책임. 해당 도메인 어댑터가 이미 있으면 `implements`만 추가한다.

**5) 프레젠테이션** — `@RequestMapping`에 `/api/v1`을 넣지 않는다(설정이 붙인다). DTO 필드는 **단위 접미사 풀네임**(`...Meters`·`...SecondsPerKm`·`...Kg`·`...Cm`, `...Spm`만 약어). Bean Validation 메시지는 **`api-spec.md` 문구 그대로** — 400 응답 본문이 된다.

**6) 테스트** — `docs/code-convention.md`와 `UserVoTest`·`UserOnboardTest`·`UserPersistenceAdapterTest`를 따른다. 도메인 예외는 타입과 메시지를 함께 검증한다. 핸들러는 성공·실패 경로와 실패 후 작업이 중단되는지 검증한다.

## 검증

```bash
cd running-service && ./gradlew test
```

`.env`가 없으면 통합 컨텍스트 로드가 실패한다. 그때는 `compileJava compileTestJava` + 도메인 테스트만 돌리고 **못 돌렸다는 사실을 말한다.**

확인할 것: 응답 필드명·상태 코드·에러 코드가 명세와 같은가 / 새 포트가 단일 메서드인가 / 새 ErrorCode가 `EXPOSED_CODES`까지 갔는가.

## WebSocket은 예외

`api-spec.md` 5번의 WebSocket 메시지는 구현 선례가 없다. 세션 상태·ack·Redis 버퍼링 키를 첫 구현에서 정하게 되므로, REST 절차를 적용하지 말고 설계를 먼저 합의한다.
