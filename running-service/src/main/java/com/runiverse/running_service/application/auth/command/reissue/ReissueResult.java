package com.runiverse.running_service.application.auth.command.reissue;

public record ReissueResult(
        String accessToken,
        String refreshToken
) {
}
