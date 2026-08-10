package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.onboard.CompleteOnboardCommand;
import com.runiverse.running_service.application.user.command.onboard.CompleteOnboardResult;

public interface CompleteOnboardUsecase {

    CompleteOnboardResult handle(CompleteOnboardCommand command);
}
