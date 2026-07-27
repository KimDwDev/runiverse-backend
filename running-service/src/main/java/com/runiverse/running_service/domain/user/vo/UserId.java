package com.runiverse.running_service.domain.user.vo;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "사용자 ID는 필수입니다.");

        if (value.version() != 7) {
            throw new IllegalArgumentException("사용자 ID는 UUIDv7 형식이어야 합니다.");
        }
    }
}
