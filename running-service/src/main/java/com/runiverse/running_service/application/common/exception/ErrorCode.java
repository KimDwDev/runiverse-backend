package com.runiverse.running_service.application.common.exception;

public sealed interface ErrorCode permits AuthErrorCode, UserErrorCode {

    String getCode();

    String getMessage();
}
