package com.runiverse.running_service.presentation.user.response;

import java.util.UUID;

public record LoginResponse(
        UUID userId,
        String accessToken,
        String refreshToken,
        boolean isOnboarded
) {
}
