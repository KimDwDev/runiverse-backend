package com.runiverse.running_service.infrastructure.oauth.google;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "oauth.google")
@Validated
public record GoogleOauthProperties(
        @NotBlank String clientId,
        String clientSecret,      // 모바일 클라이언트는 secret이 없다 — 비우면 토큰 요청에 싣지 않는다
        @NotBlank String redirectUri,
        @NotBlank String tokenUri,
        @NotBlank String userInfoUri
) {

}
