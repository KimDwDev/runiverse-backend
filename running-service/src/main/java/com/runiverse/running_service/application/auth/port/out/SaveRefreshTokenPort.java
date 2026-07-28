package com.runiverse.running_service.application.auth.port.out;

import com.runiverse.running_service.domain.user.vo.UserId;

public interface SaveRefreshTokenPort {
    void save(UserId userId, String refreshToken);
}
