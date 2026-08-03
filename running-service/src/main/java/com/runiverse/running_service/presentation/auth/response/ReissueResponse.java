package com.runiverse.running_service.presentation.auth.response;

public record ReissueResponse(
        String accessToken,
        String refreshToken
) {
}
