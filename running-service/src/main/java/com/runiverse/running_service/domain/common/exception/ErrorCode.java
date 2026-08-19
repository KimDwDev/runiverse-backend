package com.runiverse.running_service.domain.common.exception;

public sealed interface ErrorCode permits UserErrorCode, UserOnboardingErrorCode, OauthUserErrorCode,
        RunningRoomErrorCode, RunningPlayerErrorCode {

    String getCode();

    String getMessage();
}
