package com.runiverse.running_service.application.device.port.out;

import com.runiverse.running_service.domain.device.AppVersion;
import com.runiverse.running_service.domain.device.vo.Platform;

import java.util.Optional;

public interface LoadAppVersionPort {

    Optional<AppVersion> load(Platform platform);
}
