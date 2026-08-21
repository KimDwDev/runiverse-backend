package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.aggregate.RunningPlayer;

public interface SaveRunningPlayerPort {

    RunningPlayer save(RunningPlayer player);
}
