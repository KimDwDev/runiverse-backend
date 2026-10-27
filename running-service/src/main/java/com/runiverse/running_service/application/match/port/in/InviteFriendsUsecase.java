package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsCommand;
import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsResult;

public interface InviteFriendsUsecase {

    InviteFriendsResult handle(InviteFriendsCommand command);
}
