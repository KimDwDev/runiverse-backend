package com.runiverse.running_service.presentation.common.exception;

import com.runiverse.running_service.application.common.exception.ErrorCode;
import com.runiverse.running_service.presentation.common.response.ErrorResponse;
import org.springframework.http.HttpStatus;

import java.util.Set;

// 그대로 노출하는 에러 코드 추가
public final class ErrorExposurePolicy {

    private static final Set<String> EXPOSED_CODES = Set.of(
            ErrorCode.EMAIL_ALREADY_EXISTS.getCode(),
            ErrorCode.NICKNAME_ALREADY_EXISTS.getCode(),
            ErrorCode.ALREADY_ONBOARD.getCode(),
            ErrorCode.INVALID_CREDENTIALS.getCode(),
            ErrorCode.OAUTH_CODE_EXCHANGE_FAILED.getCode(),
            ErrorCode.OAUTH_EMAIL_NOT_PROVIDED.getCode(),
            ErrorCode.UNSUPPORTED_PROVIDER.getCode(),
            ErrorCode.INVALID_REFRESH_TOKEN.getCode(),
            AuthErrorCode.TOKEN_EXPIRED.getCode(),
            AuthErrorCode.TOKEN_BLOCKED.getCode(),
            AuthErrorCode.INVALID_TOKEN.getCode(),
            AuthErrorCode.AUTHENTICATION_REQUIRED.getCode()
    );
    private ErrorExposurePolicy() {} // 다른 곳에서 사용하지 못하도록
    // 400 에러나 내가 노출을 허용한 에러에 경우를 검증하는 메서드
    public static boolean isExposed(HttpStatus status, String code) {
        return status == HttpStatus.BAD_REQUEST || EXPOSED_CODES.contains(code);
    }
    // 그냥 평범한 에러는 모두 500서버 에러로 넘길 예정
    public static ErrorResponse masked() {
        return new ErrorResponse(
                CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
        );
    }
}
