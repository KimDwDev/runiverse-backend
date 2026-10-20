package com.runiverse.running_service.application.device.port.out;

import com.runiverse.running_service.domain.device.AppVersionPolicy;
import com.runiverse.running_service.domain.device.vo.Platform;

import java.util.Optional;

public interface LoadAppVersionPolicyPort {

    Optional<AppVersionPolicy> load(Platform platform);
}
