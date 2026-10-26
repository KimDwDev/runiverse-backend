package com.runiverse.running_service.application.invite.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;

public class InviteStartAtOutOfRangeException extends BusinessException {

    public InviteStartAtOutOfRangeException() {
        super(InviteErrorCode.INVITE_START_AT_OUT_OF_RANGE);
    }
}
