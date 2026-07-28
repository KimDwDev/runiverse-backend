package com.runiverse.running_service.application.auth.port.out;

import com.runiverse.running_service.domain.user.vo.UserId;

import java.util.Optional;

public interface LoadRefreshTokenPort {
    Optional<String> load(UserId userId);
}
