package com.runiverse.running_service.infrastructure.security.jwt;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.user.vo.UserId;
import com.runiverse.running_service.infrastructure.security.jwt.config.JwtDecoderConfig;
import com.runiverse.running_service.infrastructure.security.jwt.config.JwtEncoderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

public class JwtTokenAdapterTest {

    private static final String ISSUER = "runiverse";
    private static final String AUDIENCE = "runiverse-api";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    private JwtTokenAdapter jwtTokenAdapter;
    private JwtDecoder accessTokenDecoder;
    private UserId userId;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                ISSUER,
                AUDIENCE,
                new JwtProperties.TokenSpec("access-secret-for-test-must-be-at-least-32-bytes", ACCESS_TTL),
                new JwtProperties.TokenSpec("refresh-secret-for-test-must-be-at-least-32-bytes", REFRESH_TTL)
        );

        // 목이 아닌 실제 빈을 조립해 발급-검증 경로가 맞물리는지 확인한다
        JwtEncoderConfig jwtEncoderConfig = new JwtEncoderConfig();
        JwtDecoderConfig jwtDecoderConfig = new JwtDecoderConfig();
        accessTokenDecoder = jwtDecoderConfig.accessTokenDecoder(jwtProperties);

        jwtTokenAdapter = new JwtTokenAdapter(
                jwtEncoderConfig.accessTokenEncoder(jwtProperties),
                jwtEncoderConfig.refreshTokenEncoder(jwtProperties),
                jwtProperties
        );

        userId = new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    @Test
    @DisplayName("access token에 issuer, audience, subject, typ 클레임을 담는다")
    void generateAccessTokenContainsClaims() {
        // when
        Jwt jwt = accessTokenDecoder.decode(jwtTokenAdapter.generateAccessToken(userId));

        // then
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getAudience()).containsExactly(AUDIENCE);
        assertThat(jwt.getSubject()).isEqualTo(userId.value().toString());
        assertThat(jwt.getClaimAsString("typ")).isEqualTo("access");
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    @DisplayName("access token 만료는 발급 시각 + access TTL이다")
    void generateAccessTokenAppliesAccessTtl() {
        // when
        Jwt jwt = accessTokenDecoder.decode(jwtTokenAdapter.generateAccessToken(userId));

        // then
        assertThat(jwt.getExpiresAt()).isCloseTo(
                jwt.getIssuedAt().plus(ACCESS_TTL),
                within(1, ChronoUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("호출할 때마다 jti가 다른 토큰을 발급한다")
    void generateAccessTokenIssuesUniqueJti() {
        // when
        Jwt first = accessTokenDecoder.decode(jwtTokenAdapter.generateAccessToken(userId));
        Jwt second = accessTokenDecoder.decode(jwtTokenAdapter.generateAccessToken(userId));

        // then
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    @DisplayName("refresh token은 세 부분으로 이루어진 JWT를 반환한다")
    void generateRefreshTokenReturnsJwt() {
        // when
        String refreshToken = jwtTokenAdapter.generateRefreshToken(userId);

        // then
        assertThat(refreshToken.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("refresh token은 키가 달라 access 디코더로 검증되지 않는다")
    void refreshTokenIsRejectedByAccessDecoder() {
        // given
        String refreshToken = jwtTokenAdapter.generateRefreshToken(userId);

        // when & then
        assertThatThrownBy(() -> accessTokenDecoder.decode(refreshToken))
                .isInstanceOf(JwtException.class);
    }
}
