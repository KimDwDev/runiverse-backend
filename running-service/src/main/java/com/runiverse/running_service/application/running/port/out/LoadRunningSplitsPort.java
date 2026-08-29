package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import org.springframework.boot.SpringApplication;

import java.util.List;

public interface LoadRunningSplitsPort {

    List<SpringApplication.Running> loadSplits(RunningRoomId runningRoomId);
}
