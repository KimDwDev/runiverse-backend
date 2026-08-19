package com.runiverse.running_service.domain.running.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class InvalidMemberCountException extends BusinessException {

    public InvalidMemberCountException() {
        super(RunningRoomErrorCode.INVALID_MEMBER_COUNT);
    }
}
