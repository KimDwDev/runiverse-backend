package com.runiverse.running_service.domain.running.invitation.vo;

import com.runiverse.running_service.domain.running.invitation.exception.InvalidRunningRoomInvitationIdException;

public record RunningRoomInvitationId(Long value) {

    public RunningRoomInvitationId {
        if (value == null || value < 1) {
            throw new InvalidRunningRoomInvitationIdException();
        }
    }
}
