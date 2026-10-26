package com.runiverse.running_service.application.invite.port.in;

import com.runiverse.running_service.application.invite.command.accept.AcceptInvitationCommand;
import com.runiverse.running_service.application.invite.command.accept.AcceptInvitationResult;

public interface AcceptInvitationUsecase {

    AcceptInvitationResult handle(AcceptInvitationCommand command);
}
