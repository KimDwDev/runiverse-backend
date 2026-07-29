package com.runiverse.running_service.presentation.user.response;

public record ReissueResponse(
        String accessToken,
        String refreshToken
) {
}
