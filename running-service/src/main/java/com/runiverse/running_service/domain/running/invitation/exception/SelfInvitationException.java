package com.runiverse.running_service.domain.running.invitation.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomInvitationErrorCode;

public class SelfInvitationException extends BusinessException {

    public SelfInvitationException() {
        super(RunningRoomInvitationErrorCode.SELF_INVITATION);
    }
}
