package com.runiverse.running_service.infrastructure.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String issuer,
    String audience,
    TokenSpec accessToken,
    TokenSpec refreshToken
) {
    public record TokenSpec(String secret, Duration ttl) {}
}
