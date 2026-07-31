package com.runiverse.running_service.application.auth.command.oauthlogin;

public record OauthLoginCommand(
        String provider, // google, kakao등 다양한 방향에서 통일할 계획이기 때문이다.
        String authorizationCode,
        String codeVerifier
) {
}
