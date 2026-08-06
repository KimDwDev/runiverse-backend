# 레이어 패턴

문서가 정책 기준이고 기존 파일은 구현 형태의 예시다. 둘 중 어느 쪽이든 낡았을 수 있으니, 어긋나면 한쪽을 그대로 따르지 말고 차이를 알린다.

경로는 전부 `running-service/src/main/java/com/runiverse/running_service/` 기준.

## 도메인 VO — `domain/user/vo/`

열어볼 것: `AvgPace`, `Nickname`, `UserId`

- `record` 컴팩트 생성자에서 파라미터에 재대입해 정규화한 값을 필드에 넣는다.
- 접근자는 의미가 드러나게 짓고 무조건 `value()`로 만들지 않는다.
- 규칙 하나에 예외 클래스 하나. 예외는 `domain/<도메인>/exception/`, 코드·메시지는 `domain/common/exception/ErrorCode`.

## 도메인 애그리거트 — `domain/user/aggregate/`

열어볼 것: `User`, `UserOnboard`

- 생성자는 원시 값을 VO로 감싼다. 검증은 VO에 위임하고 애그리거트는 내부 상태의 불변식만 본다.
- nullable한 내부 상태는 `Optional`로 노출한다.
- 값 성격의 내부 엔티티는 수정 시 새 인스턴스를 반환한다(`UserOnboard.change(...)`).
- 동등성은 식별자 기준으로 재정의한다.

## 유스케이스 — `application/auth/command/signup/`, `login/`

열어볼 것: `SignUpHandler`, `LoginHandler`

- Command·Result는 원시 타입·UUID를 사용한다. 도메인 VO를 컨트롤러까지 노출하지 않는다.
- `@Transactional`은 **스프링 것**(`org.springframework.transaction.annotation`)을 쓴다.
- 조회 전용이면 `@Transactional(readOnly = true)`.
- Handler는 조립과 순서만 제어한다. 값 규칙은 도메인, 저장·조회는 포트가 맡는다.

## 포트 — `application/auth/port/`

열어볼 것: `port/in/SignUpUsecase`, `port/out/CheckEmailDuplicatePort`

- `port/out`은 작고 응집되게 나누고(사용 유스케이스나 변경 이유가 다르면 분리) 동작·역할이 드러나는 이름을 붙인다. `Check*`·`Load*`·`Save*`·`Delete*`·`Generate*`·`Exchange*`는 기존 예시이며 접두사 제한 목록이 아니다.
- 파라미터·반환에 도메인 타입(`UserId`, `User`)을 써도 된다. 포트는 application 소유라 domain 의존은 방향이 맞다.
- `port/out`에는 아웃바운드 인터페이스와 그 전용 입출력 모델만 둔다(예: `OauthProfile`). 여러 레이어가 함께 쓰는 DTO는 기능 패키지에 둔다.

## JPA 엔티티 — `infrastructure/persistence/user/`

열어볼 것: `UserOnboardJpaEntity`(제약 총집합), `UserJpaEntity`

- 컬럼 제약(`nullable`·`length`·`precision`/`scale`)과 `@Check`·`@UniqueConstraint`를 `erd.md` 표 그대로 옮긴다. 이름은 `uk_<테이블>_<컬럼>` / `ck_...` / `fk_<테이블>_<참조테이블>`.
- setter를 두지 않는다. `@NoArgsConstructor(access = PROTECTED)` + private 생성자 + static `create(...)`.
- 감사 컬럼은 `@CreatedDate`·`@LastModifiedDate`와 `AuditingEntityListener`를 사용한다(`erd.md` §0). `@EnableJpaAuditing` 설정이 없으면 한 번만 추가한다. 기존 엔티티는 아직 `@CreationTimestamp`를 쓴다 — 새 엔티티는 문서대로 만들고, 기존 엔티티 이관은 요청받았을 때만 한다.
- FK 제약은 걸되 값을 직접 관리할 때는 `insertable = false, updatable = false` 연관을 별도로 둔다.
- CASCADE와 논리 참조는 `erd.md` §0의 `user_id` FK 정책을 따른다. 논리 참조 테이블에는 FK 제약을 걸지 않는다.

## 영속성 어댑터 — `infrastructure/persistence/user/UserPersistenceAdapter`

- JPQL 텍스트 블록에는 테이블명이 아닌 엔티티명(`UserJpaEntity`)을 쓴다.
- 단건 조회는 `getResultStream().findFirst()`로 받아 `Optional`로 돌려준다. `getSingleResult()`는 없으면 예외를 던진다.
- 도메인과 DB 표현이 다르면 변환을 어댑터에서 흡수한다.

## Redis 어댑터 — `infrastructure/redis/`

- 키는 `RedisKey` enum으로 조립한다. 문자열을 직접 이어 붙이지 않는다. 새 종류가 필요하면 enum에 prefix를 추가한다.
- TTL 같은 운영값은 `@ConfigurationProperties`에서 읽고 하드코딩하지 않는다.

## 컨트롤러 — `presentation/auth/controller/AuthController`

- 필드는 `*Usecase` 인터페이스 타입으로 받고 Handler 구현체를 직접 주입하지 않는다.
- `try/catch`를 쓰지 않는다. 예외는 `GlobalExceptionHandler`가 일괄 변환한다.
- 인증은 `@AuthenticationPrincipal Jwt jwt` → `UUID.fromString(jwt.getSubject())`.
- 본문 없는 응답은 `ResponseEntity.noContent().build()`.

## 요청·응답 DTO — `presentation/user/request/OnboardRequest`

- 필수 숫자는 `Integer`와 `@NotNull`로 받는다. `int`는 누락을 0으로 바꾼다.
- enum은 `String`과 `@Pattern`으로 검증한다. enum 타입은 Jackson의 `MALFORMED_REQUEST_BODY`가 먼저 발생해 명세 메시지를 제어할 수 없다.
- 필드명에는 `api-convention.md`의 단위 접미사를 붙인다(예: `weightKg`·`totalDistanceMeters`·`averagePaceSecondsPerKm`·`speedMetersPerSecond`). 메시지는 `api-spec.md` 문구 그대로 쓴다.
