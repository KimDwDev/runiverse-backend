package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationCommand;

public interface UpdateRunningLocationUsecase {

    void handle(UpdateRunningLocationCommand command);
}
