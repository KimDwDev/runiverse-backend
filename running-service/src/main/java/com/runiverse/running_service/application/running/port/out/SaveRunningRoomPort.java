package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoom;

public interface SaveRunningRoomPort {

    RunningRoom save(RunningRoom room);
}
