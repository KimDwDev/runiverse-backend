package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.InvalidUserIdFormatException;
import com.runiverse.running_service.domain.user.exception.UserIdRequiredException;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        if (value == null) throw new UserIdRequiredException();

        if (value.version() != 7) throw new InvalidUserIdFormatException();
    }
}
