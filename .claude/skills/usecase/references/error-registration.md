# 에러 처리

## `BusinessException`이 두 개다

| | `domain.common.exception` | `application.common.exception` |
|---|---|---|
| 언제 | VO 생성·애그리거트 상태 전이 위반 | 유스케이스가 요청을 튕겨낼 때 |
| 위치 | `domain/<도메인>/exception/` | `application/<도메인>/exception/` |
| 응답 | **항상 500** | `toStatus()` + 노출 정책 통과 시 그 상태 |

값만 보고 판단하면 도메인, 저장소·외부 상태가 필요하면 애플리케이션 예외다.

## 도메인 예외가 500인 이유

`handleDomainException`은 항상 500을 반환한다. **400 검증은 Request DTO의 Bean Validation이 담당한다.** VO는 이중 방어용이며, 값 규칙은 DTO와 VO에서 함께 바꾼다.

## 등록 3곳

1. **`application/common/exception/ErrorCode`** — 코드 문자열·메시지를 `api-spec.md`와 글자 그대로 맞춘다.
2. **`GlobalExceptionHandler.toStatus()`** — `default`가 없는 exhaustive switch라 빠뜨리면 컴파일이 깨진다.
3. **`ErrorExposurePolicy.EXPOSED_CODES`** — 없으면 **아무 경고 없이** 응답이 500으로 바뀐다.

## 노출 정책

`isExposed`는 상태가 400이거나 코드가 `EXPOSED_CODES`에 있을 때만 참이다. 현재 그 외의 응답은 `ErrorExposurePolicy.masked()`가 500 `INTERNAL_SERVER_ERROR`로 바꾼다.

## 명세 계약과 현재 구현 차이

- 목표 계약(`api-spec.md` §0): Bean Validation 실패는 `VALIDATION_FAILED`, 마스킹하거나 예상하지 못한 500은 `INTERNAL_ERROR`다.
- 현재 구현: `GlobalExceptionHandler`는 Bean Validation 실패에 `INVALID_REQUEST`, `ErrorExposurePolicy.masked()`와 예상하지 못한 예외에 `INTERNAL_SERVER_ERROR`를 반환한다.

현재 동작을 목표 계약으로 설명하지 않는다. 관련 에러 경로를 구현할 때 차이와 영향을 알리고, 공개 계약 변경 범위를 확인한 뒤 맞춘다.

## 일부러 감추는 경우

기존 코드가 `EXPOSED_CODES`에 없으면 추가하기 전에 `git log -S <코드명>`으로 의도적 비노출인지 확인한다. 의도된 비노출은 이유를 주석이나 결정 문서에 남긴다.

## 컨트롤러 앞단의 에러

`@Valid`·JSON 파싱 실패는 `presentation/common/exception`, 인증 실패는 `AuthErrorCode`와 `JwtAuthenticationEntryPoint`가 담당한다. 응답 코드·메시지는 기존 상수명이 아닌 `api-spec.md`를 기준으로 대조한다.
