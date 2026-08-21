package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.solo.StartSoloRunningCommand;
import com.runiverse.running_service.application.running.command.solo.StartSoloRunningResult;

public interface StartSoloRunningUsecase {

    StartSoloRunningResult handle(StartSoloRunningCommand command);
}
