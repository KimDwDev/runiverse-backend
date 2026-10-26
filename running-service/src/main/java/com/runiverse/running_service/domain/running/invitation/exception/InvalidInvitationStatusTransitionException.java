package com.runiverse.running_service.domain.running.invitation.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomInvitationErrorCode;

public class InvalidInvitationStatusTransitionException extends BusinessException {

    public InvalidInvitationStatusTransitionException() {
        super(RunningRoomInvitationErrorCode.INVALID_INVITATION_STATUS_TRANSITION);
    }
}
