package com.runiverse.running_service.application.invite.port.in;

import com.runiverse.running_service.application.invite.command.send.InviteFriendsCommand;
import com.runiverse.running_service.application.invite.command.send.InviteFriendsResult;

public interface InviteFriendsUsecase {

    InviteFriendsResult handle(InviteFriendsCommand command);
}
