package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.stream.CloseUserStreamCommand;

public interface CloseUserStreamUsecase {

    void handle(CloseUserStreamCommand command);
}
