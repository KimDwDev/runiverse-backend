package com.runiverse.running_service.infrastructure.security.jwt;

import com.runiverse.running_service.application.user.port.out.GenerateTokenPort;
import com.runiverse.running_service.domain.user.vo.UserId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenAdapter implements GenerateTokenPort {
    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtEncoder accessTokenEncoder;
    private final JwtEncoder refreshTokenEncoder;
    private final JwtProperties properties;

    public JwtTokenAdapter(
        @Qualifier("accessTokenEncoder") JwtEncoder accessTokenEncoder,
        @Qualifier("refreshTokenEncoder") JwtEncoder refreshTokenEncoder,
        JwtProperties properties
    ) {
        this.accessTokenEncoder = accessTokenEncoder;
        this.refreshTokenEncoder = refreshTokenEncoder;
        this.properties = properties;
    }

    @Override
    public String generateAccessToken(UserId userId) {
        return generate(accessTokenEncoder, userId, TYPE_ACCESS, properties.accessToken().ttl());
    }

    @Override
    public String generateRefreshToken(UserId userId) {
        return generate(refreshTokenEncoder, userId, TYPE_REFRESH, properties.refreshToken().ttl());
    }

    private String generate(JwtEncoder encoder, UserId userId, String tokenType, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(userId.value().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .build();
        return encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)
        ).getTokenValue();
    }
}
