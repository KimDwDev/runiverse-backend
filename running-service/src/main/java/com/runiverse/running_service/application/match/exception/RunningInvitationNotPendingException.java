package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.InviteErrorCode;

// 이미 수락·거절했거나 나갔다 — 다시 들어가려면 다시 초대받아야 한다
public class RunningInvitationNotPendingException extends BusinessException {

    public RunningInvitationNotPendingException() {
        super(InviteErrorCode.RUNNING_INVITATION_NOT_PENDING);
    }
}
