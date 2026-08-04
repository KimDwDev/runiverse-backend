package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.GenerateTokenPort;
import com.runiverse.running_service.application.auth.port.out.RefreshTokenHashPort;
import com.runiverse.running_service.domain.user.vo.UserId;

public class FakeTokenProvider implements GenerateTokenPort, RefreshTokenHashPort {
    private static final String ACCESS_PREFIX = "access.";
    private static final String REFRESH_PREFIX = "refresh.";
    // 호출할 때마다 다른 토큰이 나와야 회전(rotation)을 검증할 수 있다
    private int sequence = 0;
    @Override
    public String generateAccessToken(UserId userId) {
        return ACCESS_PREFIX + userId.value() + "." + (++sequence);
    }
    @Override
    public String generateRefreshToken(UserId userId) {
        return REFRESH_PREFIX + userId.value() + "." + (++sequence);
    }
    @Override
    public String hash(String refreshToken) {
        return "sha256:" + refreshToken;
    }
    @Override
    public boolean matches(String refreshToken, String storedHash) {
        return hash(refreshToken).equals(storedHash);
    }
}
