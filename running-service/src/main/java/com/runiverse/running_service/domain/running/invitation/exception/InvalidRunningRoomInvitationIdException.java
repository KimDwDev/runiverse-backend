package com.runiverse.running_service.domain.running.invitation.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomInvitationErrorCode;

public class InvalidRunningRoomInvitationIdException extends BusinessException {

    public InvalidRunningRoomInvitationIdException() {
        super(RunningRoomInvitationErrorCode.INVALID_RUNNING_ROOM_INVITATION_ID);
    }
}
