package com.runiverse.running_service.unit_test.presentation;

import com.runiverse.running_service.application.common.exception.ErrorCode;
import com.runiverse.running_service.presentation.common.exception.AuthErrorCode;
import com.runiverse.running_service.presentation.common.exception.ErrorExposurePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("에러 노출 정책 단위 테스트")
public class ErrorExposurePolicyTest {

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {
            "EMAIL_VERIFICATION_COOLDOWN",
            "EMAIL_VERIFICATION_DAILY_LIMIT_EXCEEDED",
            "EMAIL_VERIFICATION_NOT_FOUND",
            "INVALID_VERIFICATION_CODE",
            "TOO_MANY_VERIFICATION_ATTEMPTS",
            "EMAIL_NOT_VERIFIED",
            "EMAIL_SEND_FAILED"
    })
    @DisplayName("이메일 인증 관련 에러는 400이 아니어도 그대로 노출한다")
    void emailVerificationErrorsAreExposed(ErrorCode errorCode) {
        // 사용자가 다음에 뭘 해야 하는지 알아야 하는 에러들이라 메시지를 가리면 안 된다
        assertThat(ErrorExposurePolicy.isExposed(HttpStatus.TOO_MANY_REQUESTS, errorCode.getCode())).isTrue();
    }

    @Test
    @DisplayName("본인이 아닌 요청 거부는 403 그대로 노출한다")
    void accessDeniedIsExposed() {
        // 노출 목록에서 빠지면 아무 경고 없이 500으로 바뀌어 클라가 원인을 알 수 없다
        assertThat(ErrorExposurePolicy.isExposed(
                HttpStatus.FORBIDDEN, AuthErrorCode.ACCESS_DENIED.getCode())).isTrue();
    }

    @Test
    @DisplayName("400 응답은 코드와 상관없이 노출한다")
    void badRequestIsAlwaysExposed() {
        // when & then
        assertThat(ErrorExposurePolicy.isExposed(HttpStatus.BAD_REQUEST, "ANY_UNKNOWN_CODE")).isTrue();
    }

    @Test
    @DisplayName("허용 목록에 없는 5xx 에러는 노출하지 않는다")
    void unknownServerErrorIsNotExposed() {
        // 내부 사정이 그대로 나가면 안 된다
        assertThat(ErrorExposurePolicy.isExposed(HttpStatus.INTERNAL_SERVER_ERROR, "SOME_INTERNAL_CODE")).isFalse();
    }

    @Test
    @DisplayName("가려진 응답은 항상 같은 500 메시지를 쓴다")
    void maskedResponseIsGeneric() {
        // when & then
        assertThat(ErrorExposurePolicy.masked().code()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}
