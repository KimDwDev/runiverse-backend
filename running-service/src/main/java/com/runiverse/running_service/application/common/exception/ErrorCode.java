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
    ALREADY_ONBOARD("ALREADY_ONBOARD", "이미 온보딩을 완료했습니다."),
    USER_NOT_FOUND("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    NICKNAME_ALREADY_EXISTS("NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다."),
    INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", "리프레시 토큰이 유효하지 않습니다."),
    UNSUPPORTED_PROVIDER("UNSUPPORTED_PROVIDER", "지원하지 않는 소셜 로그인입니다."),
    OAUTH_CODE_EXCHANGE_FAILED("OAUTH_CODE_EXCHANGE_FAILED", "소셜 로그인에 실패했습니다. 다시 시도해 주세요."),
    OAUTH_EMAIL_NOT_PROVIDED("OAUTH_EMAIL_NOT_PROVIDED", "이메일 제공에 동의해야 소셜 로그인을 할 수 있습니다.");
    private final String code;
    private final String message;
}
