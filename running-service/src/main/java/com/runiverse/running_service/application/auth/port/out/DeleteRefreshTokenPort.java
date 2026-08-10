package com.runiverse.running_service.application.auth.port.out;

import com.runiverse.running_service.domain.user.vo.UserId;

public interface DeleteRefreshTokenPort {

    void delete(UserId userId);
}
