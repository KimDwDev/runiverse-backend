package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    ALREADY_ONBOARDED("ALREADY_ONBOARDED", "이미 온보딩을 완료했습니다."),
    ONBOARDING_NOT_COMPLETED("ONBOARDING_NOT_COMPLETED", "온보딩을 먼저 완료해 주세요."),
    NICKNAME_ALREADY_EXISTS("NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다."),
    PROFILE_NOT_FOUND("PROFILE_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    PROFILE_IMAGE_NOT_UPLOADED("PROFILE_IMAGE_NOT_UPLOADED", "업로드되지 않은 이미지입니다."),
    INVALID_PROFILE_IMAGE("INVALID_PROFILE_IMAGE", "프로필 이미지가 올바르지 않습니다.");
    private final String code;
    private final String message;
}
