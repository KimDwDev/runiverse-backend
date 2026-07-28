package com.runiverse.running_service.infrastructure.redis;

import com.runiverse.running_service.application.auth.port.out.SaveRefreshTokenPort;
import com.runiverse.running_service.domain.user.vo.UserId;
import com.runiverse.running_service.infrastructure.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class RefreshTokenRedisAdapter implements SaveRefreshTokenPort {
    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private static final String REFRESH_TOKEN = "refresh_token";

    @Override
    public void save(UserId userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                RedisKey.USER.of(userId.value().toString(), REFRESH_TOKEN),
                fingerPrint(refreshToken),
                jwtProperties.refreshToken().ttl()
        );
    }

    // refresh_token이 유저것이 맞는지 확인하는 작업을 진행하니 지문을 저장
    private String fingerPrint(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e)  {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }
}
