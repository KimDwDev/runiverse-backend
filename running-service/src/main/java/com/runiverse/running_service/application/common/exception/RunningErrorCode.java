package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningErrorCode implements ErrorCode {
    RUNNING_ALREADY_IN_PROGRESS("RUNNING_ALREADY_IN_PROGRESS", "진행 중인 러닝이 있습니다.");
    private final String code;
    private final String message;
}
