package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    EMAIL_ALREADY_EXISTS("U101", "이미 사용 중인 이메일입니다."),
    INVALID_EMAIL_CREDENTIALS("U102", "이메일이 존재하지 않습니다."),
    INVALID_PASSWORD_CREDENTIALS("U103", "비밀번호가 올바르지 않습니다.");
    private final String code;
    private final String message;
}
