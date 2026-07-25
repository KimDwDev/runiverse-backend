package com.runiverse.running_service.infrastructure.redis;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.user.vo.UserId;
import com.runiverse.running_service.infrastructure.security.jwt.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenRedisAdapterTest {

    // "test-refresh-token"의 SHA-256 (sha256sum으로 확인한 값)
    private static final String REFRESH_TOKEN = "test-refresh-token";
    private static final String EXPECTED_FINGERPRINT =
            "0a9b110d5e553bd98e9965c70a601c15c36805016ba60d54f20f5830c39edcde";

    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RefreshTokenRedisAdapter refreshTokenRedisAdapter;
    private UserId userId;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                "runiverse",
                "runiverse-api",
                new JwtProperties.TokenSpec("access-secret-for-test-must-be-at-least-32-bytes", Duration.ofMinutes(30)),
                new JwtProperties.TokenSpec("refresh-secret-for-test-must-be-at-least-32-bytes", REFRESH_TTL)
        );

        refreshTokenRedisAdapter = new RefreshTokenRedisAdapter(redisTemplate, jwtProperties);
        userId = new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    @Test
    @DisplayName("user:{userId}:refresh_token 키에 refresh token TTL과 함께 저장한다")
    void saveUsesExpectedKeyAndTtl() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // when
        refreshTokenRedisAdapter.save(userId, REFRESH_TOKEN);

        // then
        verify(valueOperations).set(
                "user:" + userId.value() + ":refresh_token",
                EXPECTED_FINGERPRINT,
                REFRESH_TTL
        );
    }

    @Test
    @DisplayName("토큰 원문이 아니라 SHA-256 지문을 저장한다")
    void saveStoresFingerprintInsteadOfRawToken() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ArgumentCaptor<String> storedValue = ArgumentCaptor.forClass(String.class);

        // when
        refreshTokenRedisAdapter.save(userId, REFRESH_TOKEN);

        // then
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.anyString(),
                storedValue.capture(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );

        assertThat(storedValue.getValue())
                .isNotEqualTo(REFRESH_TOKEN)
                .hasSize(64)
                .isEqualTo(EXPECTED_FINGERPRINT);
    }
}
