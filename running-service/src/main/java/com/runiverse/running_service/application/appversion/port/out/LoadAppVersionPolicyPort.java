package com.runiverse.running_service.application.appversion.port.out;

import com.runiverse.running_service.domain.appversion.AppVersionPolicy;
import com.runiverse.running_service.domain.appversion.vo.Platform;

import java.util.Optional;

public interface LoadAppVersionPolicyPort {

    Optional<AppVersionPolicy> load(Platform platform);
}
