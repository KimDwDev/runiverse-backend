package com.runiverse.running_service.application.auth.port.in;

import com.runiverse.running_service.application.auth.command.logout.LogoutCommand;
import com.runiverse.running_service.application.auth.command.logout.LogoutResult;

public interface LogoutUsecase {
    LogoutResult handle(LogoutCommand command);
}
