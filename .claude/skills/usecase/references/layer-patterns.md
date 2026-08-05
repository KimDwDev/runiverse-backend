# 레이어별 — 기존 파일과, 그 파일을 봐도 모르는 것

각 레이어에서 **먼저 열어볼 파일**을 적고, 그 파일만 읽어서는 알 수 없는 규칙을 덧붙인다. 코드 모양은 실제 파일이 정본이므로 여기 옮겨 적지 않는다.

경로는 전부 `running-service/src/main/java/com/runiverse/running_service/` 기준.

## 도메인 VO — `domain/user/vo/`

열어볼 것: `AvgPace`(범위 검증), `Nickname`(정규화 + 형식), `UserId`(UUIDv7 검증)

- `record`의 컴팩트 생성자에서 파라미터에 **재대입하면 그 값이 필드로 들어간다.** `Nickname`이 `value = value.trim()`으로 정규화한 뒤 검증하는 게 그 활용이다.
- 접근자 이름은 의미가 드러나게 짓는다 — `AvgPace.secondPerKm()`처럼. 무조건 `value()`가 아니다.
- 규칙 하나에 예외 클래스 하나. 예외는 `domain/<도메인>/exception/`, 코드·메시지는 `domain/common/exception/ErrorCode`.

## 도메인 애그리거트 — `domain/user/aggregate/`

열어볼 것: `User`(연결·온보딩 상태 전이), `UserOnboard`(불변 수정)

- 생성자는 원시 값을 받아 VO로 감싼다. **검증은 VO에 위임하고 애그리거트는 불변식만 본다** — "이미 온보딩됐는데 또 하는가" 같은 내부 상태 간의 관계.
- nullable한 내부 상태는 `Optional`로 노출한다.
- 값 성격의 내부 엔티티는 수정 시 새 인스턴스를 반환한다(`UserOnboard.change(...)`).
- 동등성은 식별자 기준으로 재정의한다.

## 유스케이스 — `application/auth/command/signup/`, `login/`

열어볼 것: `SignUpHandler`(생성), `LoginHandler`(조회 + 토큰 발급)

- Command·Result는 **원시 타입·UUID로 주고받는다.** 도메인 VO를 컨트롤러까지 흘리지 않는다.
- `@Transactional`은 **스프링 것**(`org.springframework.transaction.annotation`)을 쓴다. `SignUpHandler`가 `jakarta.transaction`을 쓰고 있는데 이건 알려진 실수이므로 따라 하지 않는다.
- 조회 전용이면 `@Transactional(readOnly = true)`.
- Handler는 조립과 순서 제어만 한다. 값 규칙은 도메인, 저장·조회는 포트.
- 번호 주석(`// 1. 이메일 중복 확인`)으로 흐름을 끊어 적는 게 이 저장소 스타일이다. 자명하면 생략해도 된다.

## 포트 — `application/auth/port/`

열어볼 것: `port/in/SignUpUsecase`, `port/out/CheckEmailDuplicatePort`

- `port/out`은 단일 메서드에 동사로 시작: `Check*`(존재·일치) / `Load*` / `Save*` / `Delete*` / `Generate*` / `Exchange*`(외부 호출).
- 파라미터·반환에 도메인 타입(`UserId`, `User`)을 써도 된다. 포트는 application 소유라 domain 의존은 방향이 맞다.
- `port/out`에 인터페이스가 아닌 것을 두지 않는다. `OauthProfile`이 DTO인데 거기 있는 건 정리 대상이다.

## JPA 엔티티 — `infrastructure/persistence/user/`

열어볼 것: `UserOnboardJpaEntity`(제약 총집합), `UserJpaEntity`

- 컬럼 제약(`nullable`·`length`·`precision`/`scale`)과 `@Check`·`@UniqueConstraint`를 `erd.md` 표 그대로 옮긴다. 이름은 `uk_<테이블>_<컬럼>` / `ck_...` / `fk_<테이블>_<참조테이블>`.
- setter를 두지 않는다. `@NoArgsConstructor(access = PROTECTED)` + private 생성자 + static `create(...)`.
- 감사 컬럼은 `@CreationTimestamp`·`@UpdateTimestamp`.
- FK 제약은 걸되 값은 직접 관리하고 싶을 때, `insertable = false, updatable = false` 연관을 따로 둔다(`UserOnboardJpaEntity`의 `user` 필드).
- 탈퇴 시 CASCADE 대상인지 논리 참조인지는 `erd.md` §0의 `user_id` FK 정책을 따른다. 논리 참조 테이블에는 FK 제약을 걸지 않는다.

## 영속성 어댑터 — `infrastructure/persistence/user/UserPersistenceAdapter`

- JPQL 텍스트 블록에는 **엔티티 이름**(`UserJpaEntity`)을 쓴다. 테이블 이름이 아니다.
- 단건 조회는 `getResultStream().findFirst()`로 받아 `Optional`로 돌려준다. `getSingleResult()`는 없으면 예외를 던진다.
- 도메인의 빈 문자열 sentinel ↔ DB null 같은 변환을 여기서 흡수한다(`emptyToNull`).

## Redis 어댑터 — `infrastructure/redis/`

- 키는 `RedisKey` enum으로 조립한다. 문자열을 직접 이어 붙이지 않는다. 새 종류가 필요하면 enum에 prefix를 추가한다.
- TTL 같은 운영값은 `@ConfigurationProperties`(`JwtProperties` 등)에서 읽는다. 숫자를 코드에 박지 않는다.

## 컨트롤러 — `presentation/auth/controller/AuthController`

- 필드는 `*Usecase` **인터페이스 타입**으로 받는다. Handler 구현체를 직접 주입받지 않는다.
- `try/catch`를 쓰지 않는다. 예외는 `GlobalExceptionHandler`가 일괄 변환한다.
- 인증은 `@AuthenticationPrincipal Jwt jwt` → `UUID.fromString(jwt.getSubject())`.
- 본문 없는 응답은 `ResponseEntity.noContent().build()`.

## 요청·응답 DTO — `presentation/user/request/OnboardRequest`

두 가지가 함정이다.

**필수 숫자 필드는 `Integer`로 받는다.** `int`면 누락 시 0이 들어와 `@NotNull`이 무의미해진다.

**enum도 `String`으로 받아 `@Pattern`으로 검증한다.**

```java
@NotBlank(message = "성별은 필수입니다.")
@Pattern(regexp = "^(?i)(MALE|FEMALE)$", message = "성별은 MALE 또는 FEMALE이어야 합니다.")
String gender
```

타입 자체를 enum으로 두면 Jackson이 먼저 터져 `MALFORMED_REQUEST_BODY`가 나가고, 스펙에 적힌 메시지를 제어할 수 없다.

그 외: 필드명에 단위 접미사(`weightKg`·`totalDistanceMeters`), 메시지는 `api-spec.md` 문구 그대로.
