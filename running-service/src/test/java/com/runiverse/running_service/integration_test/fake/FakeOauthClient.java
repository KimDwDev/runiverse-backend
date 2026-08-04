package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.exception.OauthCodeExchangeFailedException;
import com.runiverse.running_service.application.auth.port.out.ExchangeOauthCodePort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.vo.Provider;

import java.util.HashMap;
import java.util.Map;

public class FakeOauthClient implements ExchangeOauthCodePort {
    private final Map<String, OauthProfile> profiles = new HashMap<>();
    private int exchangeCount = 0;
    // 테스트 준비 - 인가 코드에 대응하는 프로필을 미리 심는다
    public void register(String authorizationCode, OauthProfile profile) {
        profiles.put(authorizationCode, profile);
    }
    @Override
    public OauthProfile exchange(Provider provider, String authorizationCode, String codeVerifier) {
        exchangeCount++;
        OauthProfile profile = profiles.get(authorizationCode);
        // 코드가 없거나 provider가 어긋나면 실제 클라이언트와 같이 교환 실패로 본다
        if (profile == null || profile.provider() != provider) {
            throw new OauthCodeExchangeFailedException();
        }
        return profile;
    }
    // 검증 전용
    public int exchangeCount() {
        return exchangeCount;
    }
}
