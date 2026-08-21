package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.profile.ChangeProfileCommand;
import com.runiverse.running_service.application.user.command.profile.ChangeProfileResult;

public interface ChangeProfileUsecase {

    ChangeProfileResult handle(ChangeProfileCommand command);
}
