package com.runiverse.running_service.presentation.user.response;

import java.util.UUID;

public record MyBasicInfoResponse(
        UUID userId,
        String nickname,
        boolean isOnboarded
) {

}
