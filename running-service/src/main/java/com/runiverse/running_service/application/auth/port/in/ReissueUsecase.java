package com.runiverse.running_service.application.auth.port.in;

import com.runiverse.running_service.application.auth.command.reissue.ReissueCommand;
import com.runiverse.running_service.application.auth.command.reissue.ReissueResult;

public interface ReissueUsecase {

    ReissueResult handle(ReissueCommand command);
}
