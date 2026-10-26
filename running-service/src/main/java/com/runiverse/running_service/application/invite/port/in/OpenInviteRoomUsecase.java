package com.runiverse.running_service.application.invite.port.in;

import com.runiverse.running_service.application.invite.command.open.OpenInviteRoomCommand;
import com.runiverse.running_service.application.invite.command.open.OpenInviteRoomResult;

public interface OpenInviteRoomUsecase {

    OpenInviteRoomResult handle(OpenInviteRoomCommand command);
}
