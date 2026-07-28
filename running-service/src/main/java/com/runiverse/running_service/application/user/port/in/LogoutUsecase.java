package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.logout.LogoutCommand;
import com.runiverse.running_service.application.user.command.logout.LogoutResult;

public interface LogoutUsecase {
    LogoutResult handle(LogoutCommand command);
}
