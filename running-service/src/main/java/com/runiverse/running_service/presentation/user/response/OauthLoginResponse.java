package com.runiverse.running_service.presentation.user.response;

import java.util.UUID;

public record OauthLoginResponse(
        UUID userId,
        String accessToken,
        String refreshToken
) {
}
