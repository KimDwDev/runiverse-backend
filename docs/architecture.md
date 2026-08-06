# Architecture

클린 아키텍처 + DDD. 패키지 루트는 `com.runiverse.running_service`, 의존 방향은 항상 안쪽(domain)으로 향한다.

## 의존 방향

```
presentation ──▶ port/in ──▶ Handler ──▶ port/out ◀── infrastructure
                                │
                                ▼
                             domain
```

- `domain`은 어떤 레이어도 import하지 않는다 — Spring·JPA에도 의존하지 않는다.
- `infrastructure`의 화살표만 반대로 향하는 것이 의존성 역전(DIP)이다. 포트 인터페이스는 `application`이 소유하고 어댑터가 구현한다 — DB·Redis·외부 API를 바꿔도 안쪽은 그대로다.
- 실행은 바깥에서 안으로 들어가지만, 코드 의존은 언제나 안쪽을 가리킨다.

## 패키지 구조

```
domain/                 프레임워크 의존 없음
  <domain>/             aggregate · vo · exception
  common/exception/     BusinessException · ErrorCode (도메인용)

application/
  <domain>/command/     기능별 하위 패키지 — Command · Handler · Result 3종 세트
  <domain>/port/in/     *Usecase — Handler가 구현
  <domain>/port/out/    단일 메서드 인터페이스
  <domain>/exception/   비즈니스 규칙 위반
  common/exception/     BusinessException · ErrorCode (유스케이스용)

infrastructure/         기술별로 나눈다 — persistence · redis · security · oauth · identifier …
presentation/
  <domain>/             controller · request · response
  common/exception/     GlobalExceptionHandler · ErrorCode · ErrorExposurePolicy
```

## 요청 흐름

```
AuthController ──▶ SignUpUsecase ──▶ SignUpHandler ──▶ SaveUserPort ──▶ UserPersistenceAdapter
                     (port/in)       @Transactional      (port/out)        (infrastructure)
                                           │
                                      new User(…)  ← domain
```

DTO는 경계마다 바뀐다: `SignUpRequest` → `SignUpCommand` → `User` → `UserJpaEntity`. 변환은 경계를 넘기는 쪽이 한다 — 컨트롤러가 `Command`를 만들고, 어댑터가 `toDomain()`으로 되돌린다.

## 레이어별 규칙

- **domain/**: 프레임워크 의존 금지. VO는 생성 시점에 검증하고 도메인 예외를 던진다. 하위는 `aggregate`·`vo`·`exception`으로 나눈다.
- **application/**: 기능별 패키지에 `Command`/`Handler`/`Result` 3종 세트를 두고, `port/in`의 `*Usecase`를 구현한다. 트랜잭션 경계는 Handler(`@Transactional`)다.
- **port/out**: 단일 메서드 인터페이스로 잘게 쪼갠다 — 기존 포트에 메서드를 추가하지 말고 새 포트를 만든다. Handler는 필요한 포트만 주입받는다.
- **infrastructure/**: 아웃바운드 어댑터. 도메인 ↔ JPA 엔티티 변환은 어댑터 책임이다. 구현체는 `*Adapter`, 외부 API 클라이언트는 `*Client`로 명명한다. 같은 도메인의 여러 포트는 어댑터 하나가 함께 구현한다 — 포트마다 어댑터를 만들지 않는다.
- **presentation/**: 컨트롤러 + DTO. 예외는 `GlobalExceptionHandler`에서 일괄 변환한다.

## DDD 규칙

- **애그리거트 루트**: 루트만 외부에 노출한다. `User`가 `OauthUser`·`UserOnboard`를 내부에 들고, 포트는 루트 단위로 저장·조회한다.
- **애그리거트 행위**: 불변식은 애그리거트가 스스로 지킨다. `linkOauth`·`completeOnboarding`처럼 의도가 드러나는 메서드로만 상태를 바꾸고, 위반은 도메인 예외로 막는다. 생성 방식이 여럿이면 정적 팩토리(`User.registerWithOauth`)로 구분한다.
- **VO**: 식별자·이메일·닉네임처럼 의미가 있는 값은 원시 타입으로 두지 않는다. `record`로 만들어 불변과 값 기준 동등성을 강제하고, 생성자에서 검증한다 — 고정된 값 집합은 `enum`(`Gender`·`Provider`).
- **애그리거트 간 참조**: ID로만 한다(*Reference by Identity*). DB에도 하드 FK를 두지 않는다 — [erd.md](erd.md)와 같은 원칙이다. 애그리거트 내부는 객체 참조를 쓴다.
- **예외 구분**: VO 검증 실패는 `domain/`, 비즈니스 규칙 위반은 `application/`이다. 두 레이어에 같은 이름의 `BusinessException`·`ErrorCode`가 있으니 import 시 확인한다.

## 구현 스타일 기준

기존 구현을 참고할 때는 `application/auth/command/signup`·`login` 패키지가 기준이다.
