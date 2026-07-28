package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.signup.SignUpCommand;
import com.runiverse.running_service.application.user.command.signup.SignUpResult;

public interface SignUpUsecase {
    SignUpResult handle(SignUpCommand command);
}
