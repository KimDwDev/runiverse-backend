package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.login.LoginCommand;
import com.runiverse.running_service.application.user.command.login.LoginResult;

public interface LoginUsecase {
    LoginResult handle(LoginCommand command);
}
