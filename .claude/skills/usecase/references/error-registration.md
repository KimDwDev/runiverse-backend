# 에러 처리

## `BusinessException`이 두 개다

| | `domain.common.exception` | `application.common.exception` |
|---|---|---|
| 언제 | VO 생성·애그리거트 상태 전이 위반 | 유스케이스가 요청을 튕겨낼 때 |
| 예 | 닉네임 길이 초과, 페이스 범위 밖 | 이메일 중복, 리프레시 토큰 무효 |
| 위치 | `domain/<도메인>/exception/` | `application/<도메인>/exception/` |
| 응답 | **항상 500** | `toStatus()` + 노출 정책 통과 시 그 상태 |

**판단 기준**: 값만 보면 아는가, 저장소·외부 상태를 봐야 아는가. 닉네임이 2~16자인지는 값만 보면 안다(도메인). 남이 이미 쓰는지는 DB를 봐야 안다(애플리케이션).

## 도메인 예외가 500인 이유

`handleDomainException`이 무조건 500으로 응답한다. 도메인 규칙 위반은 "앞단에서 걸러졌어야 할 요청"이라는 전제다.

그래서 **400으로 보여줄 검증은 Request DTO의 Bean Validation이 담당한다.** VO 검증만 믿으면 400 대신 500이 나간다.

두 곳에 규칙이 겹치는 건 의도된 이중 방어다. 다만 **한쪽만 고치면 그 순간 500이 새므로**, 값 규칙을 바꿀 때 VO와 Request DTO를 같이 본다.

## 등록 3곳

1. **`application/common/exception/ErrorCode`** — 코드 문자열·메시지를 `api-spec.md`와 글자 그대로 맞춘다. 이게 응답 본문이 된다.
2. **`GlobalExceptionHandler.toStatus()`** — `default`가 없는 exhaustive switch라 빠뜨리면 컴파일이 깨진다.
3. **`ErrorExposurePolicy.EXPOSED_CODES`** — 없으면 **아무 경고 없이** 응답이 500으로 바뀐다.

## 응답이 만들어지는 경로

1. Handler가 던짐 → 2. `handleBusinessException`이 잡음 → 3. `toStatus()`가 409 계산 → 4. `respond(409, code, …)` → 5. `isExposed(409, code)` 확인 → 6. **`false`면 3번 결과를 버리고 500 `INTERNAL_ERROR`로 교체**

`isExposed`는 **상태가 400이거나** `EXPOSED_CODES`에 있을 때만 `true`다. 그래서 400 계열은 자동 통과하고, 401·403·404·409는 명시 등록해야 나간다.

## 일부러 감추는 경우

`USER_NOT_FOUND`가 그렇다 — `toStatus`는 401로 매핑하지만 목록에 없어 500으로 나가고, 커밋 `d856b8f`가 일부러 만든 동작이다.

이런 결정을 할 때는 **왜 감추는지 주석을 남긴다.** 안 그러면 다음 사람이 누락으로 보고 되돌린다. 반대로 기존 코드에서 누락을 발견했을 때도 고치기 전에 `git log -S <코드명>`으로 의도적 제거였는지 확인한다.

## 컨트롤러 앞단의 에러

`presentation/common/exception/CommonErrorCode` 담당 — `@Valid` 실패는 `INVALID_REQUEST`(400, 메시지는 위반 필드 메시지를 이어 붙인 것), JSON 파싱 실패는 `MALFORMED_REQUEST_BODY`(400).

인증 실패 4종은 `AuthErrorCode`가 갖고 `JwtAuthenticationEntryPoint`가 응답한다. 이미 `EXPOSED_CODES`에 있다.

> `CommonErrorCode.INVALID_REQUEST`·`INTERNAL_SERVER_ERROR`는 명세의 `VALIDATION_FAILED`·`INTERNAL_ERROR`와 문자열이 다르다. 알려진 불일치이고 정리 예정이니 선례로 삼지 말고, 새 코드는 명세 문자열을 그대로 쓴다.
