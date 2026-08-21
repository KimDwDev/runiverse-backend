package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.vo.Pace;

import java.util.Optional;

public interface LoadUserAvgPacePort {

    Optional<Pace> loadAvgPace(UserId userId);
}
