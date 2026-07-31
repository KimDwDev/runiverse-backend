package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 사용자
    USER_ID_REQUIRED("USER_ID_REQUIRED", "사용자 ID는 필수입니다."),
    INVALID_USER_ID_FORMAT("INVALID_USER_ID_FORMAT", "사용자 ID는 UUIDv7 형식이어야 합니다."),
    EMAIL_REQUIRED("EMAIL_REQUIRED", "이메일은 필수입니다."),
    EMAIL_TOO_LONG("EMAIL_TOO_LONG", "이메일은 254자를 초과할 수 없습니다."),
    INVALID_EMAIL_FORMAT("INVALID_EMAIL_FORMAT", "올바른 이메일 형식이 아닙니다."),
    PASSWORD_HASH_REQUIRED("PASSWORD_HASH_REQUIRED", "비밀번호 해시는 필수입니다."),
    INVALID_PASSWORD_HASH_FORMAT("INVALID_PASSWORD_HASH_FORMAT", "비밀번호 해시는 빈 값이거나 올바른 Argon2id 형식이어야 합니다."),
    DESCRIPTION_REQUIRED("DESCRIPTION_REQUIRED", "소개는 null일 수 없습니다."),
    DESCRIPTION_TOO_LONG("DESCRIPTION_TOO_LONG", "소개는 100자를 초과할 수 없습니다."),
    NICKNAME_REQUIRED("NICKNAME_REQUIRED", "닉네임은 필수입니다."),
    INVALID_NICKNAME_FORMAT("INVALID_NICKNAME_FORMAT", "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다."),
    INVALID_NICKNAME_LENGTH("INVALID_NICKNAME_LENGTH", "닉네임은 2자 이상 16자 이하로 작성해주시길 바랍니다."),
    PROVIDER_REQUIRED("PROVIDER_REQUIRED", "소셜 로그인 제공자는 필수입니다"),
    UNSUPPORTED_PROVIDER("UNSUPPORTED_PROVIDER", "지원하지 않는 소셜 로그인입니다."),
    PROVIDER_ID_REQUIRED("PROVIDER_ID_REQUIRED", "소셜 계정 식별자를 가져오지 못했습니다."),
    PROVIDER_ID_TOO_LONG("PROVIDER_ID_TOO_LONG", "소셜 계정 식별자 길이가 허용 범위를 벗어났습니다."),
    OAUTH_ALREADY_LINKED("OAUTH_ALREADY_LINKED", "이미 연결된 소셜 계정입니다."),
    OAUTH_NOT_LINKED("OAUTH_NOT_LINKED", "연결되지 않은 소셜 계정입니다."),
    LAST_SIGN_IN_METHOD("LAST_SIGN_IN_METHOD", "마지막 로그인 수단은 해제할 수 없습니다.");
    private final String code;
    private final String message;
}
