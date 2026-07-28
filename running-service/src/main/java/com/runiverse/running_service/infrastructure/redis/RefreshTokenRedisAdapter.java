package com.runiverse.running_service.infrastructure.redis;

import com.runiverse.running_service.application.auth.port.out.DeleteRefreshTokenPort;
import com.runiverse.running_service.application.auth.port.out.LoadRefreshTokenPort;
import com.runiverse.running_service.application.auth.port.out.SaveRefreshTokenHashPort;
import com.runiverse.running_service.domain.user.vo.UserId;
import com.runiverse.running_service.infrastructure.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RefreshTokenRedisAdapter implements SaveRefreshTokenHashPort, LoadRefreshTokenPort, DeleteRefreshTokenPort {

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private static final String REFRESH_TOKEN = "refresh_token";

    @Override
    public void save(UserId userId, String hashedRefreshToken) {
        redisTemplate.opsForValue().set(
                key(userId),
                hashedRefreshToken,
                jwtProperties.refreshToken().ttl()
        );
    }

    @Override
    public Optional<String> load(UserId userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    @Override
    public void delete(UserId userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(UserId userId) {
        return RedisKey.USER.of(userId.value().toString(), REFRESH_TOKEN);
    }
}
