---
name: usecase
description: >-
  Runiverse 백엔드에 새 API 엔드포인트·유스케이스·도메인 동작을 구현할 때 사용한다. api-spec.md의
  명세를 클린 아키텍처 레이어(도메인 → Command/Handler/Result와 포트 → JPA 어댑터 → 컨트롤러 → 테스트)로
  옮기는 순서, 그리고 컴파일러가 잡지 못해 런타임에 409·403을 500으로 둔갑시키는 에러 코드 등록 함정을
  다룬다. "~ API 만들어줘", "~ 기능 구현해줘", "스펙 N번 구현", "엔드포인트 추가", 핸들러·포트·컨트롤러·
  DTO·JPA 엔티티 생성이 전부 해당한다. 이 저장소에서 서버 기능을 새로 만드는 작업이면 코드를 쓰기 전에 읽는다.
---

# 유스케이스 구현

명세 한 건을 테스트가 도는 코드까지 옮긴다. 레이어 규칙·네이밍은 `docs/architecture.md`가 정본이고, 여기서는 **문서에 없는 것**만 다룬다.

## 이 저장소의 전제

- **포트는 단일 메서드다.** 기존 포트에 메서드를 추가하지 않고 새로 만든다. 대신 어댑터 하나가 여러 포트를 구현한다(`UserPersistenceAdapter`는 9개). 포트가 느는 건 정상, 어댑터가 느는 건 대개 잘못이다.
- **Spring Data Repository를 쓰지 않는다.** `EntityManager` + JPQL 텍스트 블록. `JpaRepository` 도입은 의존성 추가라 요청 없이 하지 않는다.
- **도메인 예외는 전부 500으로 마스킹된다.** 400으로 보여줄 검증은 Request DTO의 Bean Validation이 만들고, VO 검증은 뒤를 받치는 이중 방어다. 둘 다 둔다.
- **에러 코드 등록 3곳 중 1곳은 컴파일러가 못 잡는다.** 빠뜨리면 409가 조용히 500이 된다.

## 0. 읽기

해당 기능의 절만 찾아 읽는다. `api-spec.md`는 1800줄이 넘는다.

| 문서 | 확인할 것 |
|---|---|
| `api-spec.md` | 경로·필드·상태 코드·에러 케이스·**검증 메시지 문구** |
| `erd.md` | 컬럼·타입, §0 PK/FK 정책, §6 enum 사전 |
| `feature-spec.md` §2 | 화면 설명에 안 드러나는 도메인 제약 |
| `api-convention.md` | 단위 접미사·커서 페이지네이션·토글 액션 |

**스펙과 코드가 어긋나면 멈추고 사용자에게 확인한다.** 스펙은 초안이라 코드 쪽이 맞는 경우가 실제로 있다.

만들 것과 비슷한 기존 유스케이스를 열어 형태를 맞춘다. 기준은 `application/auth/command/signup`(생성)·`login`(조회+토큰).

## 1~6. 안쪽에서 바깥으로

이 순서라야 매 단계 컴파일이 유지된다. 각 레이어에서 **기존 파일을 봐도 모르는 것**은 `references/layer-patterns.md`에 있다.

이 순서는 실제 커밋 순서이기도 하다(`git log`의 온보딩 기능 참고). 작업이 길면 단계마다 커밋을 제안한다 — 단위는 `docs/git-convention.md`.

**1) 도메인** — 값 규칙이나 상태 전이가 있을 때만. VO는 `record` + 컴팩트 생성자 검증, 규칙마다 전용 예외 하나, 코드는 `domain/common/exception/ErrorCode`에. 프레임워크를 import하지 않는다.

**2) 애플리케이션** — `command/<기능>/`에 `Command`/`Handler`/`Result`, `port/in/<기능>Usecase`, 필요한 `port/out/`(단일 메서드·동사 시작). 유스케이스가 튕겨내는 조건은 `application/<도메인>/exception/`에.

**3) 에러 코드 등록** — application 예외를 만들었으면 세 곳 전부.

| 위치 | 빠뜨리면 |
|---|---|
| `application/common/exception/ErrorCode` | 컴파일 에러 |
| `GlobalExceptionHandler.toStatus()` | 컴파일 에러 (exhaustive switch) |
| `ErrorExposurePolicy.EXPOSED_CODES` | **경고 없이 통과 → 런타임에 500** |

세 번째만 위험하다. 자세한 건 `references/error-registration.md`.

**4) 인프라** — JPA 엔티티는 `erd.md` 표 그대로(제약·`@Check`·`@UniqueConstraint`), setter 없이 static `create(...)`. 도메인 ↔ 엔티티 변환은 어댑터 책임. 해당 도메인 어댑터가 이미 있으면 `implements`만 추가한다.

**5) 프레젠테이션** — `@RequestMapping`에 `/api/v1`을 넣지 않는다(설정이 붙인다). DTO 필드는 **단위 접미사 풀네임**(`...Meters`·`...SecondsPerKm`·`...Kg`·`...Cm`, `...Spm`만 약어). Bean Validation 메시지는 **`api-spec.md` 문구 그대로** — 400 응답 본문이 된다.

**6) 테스트** — 규칙은 `docs/code-convention.md`, 형태는 `UserVoTest`·`UserOnboardTest`·`UserPersistenceAdapterTest`를 열어 맞춘다. 도메인 예외는 **타입과 메시지를 같이** 검증한다(메시지가 `ErrorCode`와 어긋나면 잡히도록). 핸들러 테스트는 **선례가 없으니** 포트를 mock으로 채우고 성공 경로·실패 예외·실패 시 이후 단계 미실행(`verify(port, never())`)을 본다.

## 7. 끝내기 전에

```bash
cd running-service && ./gradlew test
```

`.env`가 없으면 통합 컨텍스트 로드가 실패한다. 그때는 `compileJava compileTestJava` + 도메인 테스트만 돌리고 **못 돌렸다는 사실을 말한다.**

확인할 것: 응답 필드명·상태 코드·에러 코드가 스펙과 같은가 / 새 포트가 단일 메서드인가 / 새 ErrorCode가 `EXPOSED_CODES`까지 갔는가.

단언 완화나 빈 catch로 테스트를 통과시키지 않는다.

## WebSocket은 예외

`api-spec.md` 5번의 WS 메시지 13종은 선례가 없다. 세션 상태·ack·Redis 버퍼링 키를 첫 구현에서 정하게 되므로, REST 절차를 적용하지 말고 설계를 먼저 합의한다.
