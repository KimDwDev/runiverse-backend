package com.runiverse.running_service.application.user.query.nickname;

import java.util.UUID;

public record CheckNicknameAvailabilityQuery(
        UUID userId,
        String nickname
) {

}
