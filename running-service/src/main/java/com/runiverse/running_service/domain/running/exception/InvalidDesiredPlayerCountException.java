package com.runiverse.running_service.domain.running.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningPlayerErrorCode;

public class InvalidDesiredPlayerCountException extends BusinessException {

    public InvalidDesiredPlayerCountException() {
        super(RunningPlayerErrorCode.INVALID_DESIRED_PLAYER_COUNT);
    }
}
