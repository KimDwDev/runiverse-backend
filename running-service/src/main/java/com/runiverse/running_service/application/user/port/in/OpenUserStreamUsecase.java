package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.stream.OpenUserStreamCommand;

public interface OpenUserStreamUsecase {

    void handle(OpenUserStreamCommand command);
}
