package com.runiverse.running_service.domain.running.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningPlayerErrorCode;

public class PlayerAlreadyLeftException extends BusinessException {

    public PlayerAlreadyLeftException() {
        super(RunningPlayerErrorCode.PLAYER_ALREADY_LEFT);
    }
}
