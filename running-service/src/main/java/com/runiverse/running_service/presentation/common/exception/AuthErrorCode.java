package com.runiverse.running_service.presentation.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode {
    AUTHENTICATION_REQUIRED("A001", "인증이 필요합니다."),
    TOKEN_EXPIRED("A002", "액세스 토큰이 만료되었습니다."),
    INVALID_TOKEN("A003", "유효하지 않은 토큰입니다.");
    private final String code;
    private final String message;
}
