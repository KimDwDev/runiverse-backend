package com.runiverse.running_service.application.invite.port.in;

import com.runiverse.running_service.application.invite.command.decline.DeclineInvitationCommand;

public interface DeclineInvitationUsecase {

    // 204라 돌려줄 것이 없다
    void handle(DeclineInvitationCommand command);
}
