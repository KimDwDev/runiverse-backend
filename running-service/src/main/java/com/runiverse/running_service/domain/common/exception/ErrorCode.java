package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 사용자
    USER_ID_REQUIRED("U000", "사용자 ID는 필수입니다."),
    INVALID_USER_ID_FORMAT("U001", "사용자 ID는 UUIDv7 형식이어야 합니다."),
    EMAIL_REQUIRED("U010", "이메일은 필수입니다."),
    EMAIL_TOO_LONG("U011", "이메일은 254자를 초과할 수 없습니다."),
    INVALID_EMAIL_FORMAT("U012", "올바른 이메일 형식이 아닙니다."),
    PASSWORD_HASH_REQUIRED("U020", "비밀번호 해시는 필수입니다."),
    INVALID_PASSWORD_HASH_FORMAT("U021", "비밀번호 해시는 빈 값이거나 올바른 Argon2id 형식이어야 합니다."),
    DESCRIPTION_REQUIRED("U030", "소개는 null일 수 없습니다."),
    DESCRIPTION_TOO_LONG("U031", "소개는 100자를 초과할 수 없습니다.");
    private final String code;
    private final String message;
}
