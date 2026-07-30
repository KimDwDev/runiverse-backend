package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다."),
    INVALID_EMAIL_CREDENTIALS("INVALID_EMAIL_CREDENTIALS", "이메일이 존재하지 않습니다."),
    INVALID_PASSWORD_CREDENTIALS("INVALID_PASSWORD_CREDENTIALS", "비밀번호가 올바르지 않습니다."),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", "리프레시 토큰이 유효하지 않습니다."),
    OAUTH_CODE_EXCHANGE_FAILED("OAUTH_CODE_EXCHANGE_FAILED", "소셜 로그인에 실패했습니다. 다시 시도해 주세요.");
    private final String code;
    private final String message;
}
