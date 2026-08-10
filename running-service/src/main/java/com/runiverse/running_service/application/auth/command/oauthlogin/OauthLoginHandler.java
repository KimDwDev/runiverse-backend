package com.runiverse.running_service.application.auth.command.oauthlogin;

import com.runiverse.running_service.application.auth.exception.UnsupportedProviderException;
import com.runiverse.running_service.application.auth.port.in.OauthLoginUsecase;
import com.runiverse.running_service.application.auth.port.out.CheckOnboardPort;
import com.runiverse.running_service.application.auth.port.out.ExchangeOauthCodePort;
import com.runiverse.running_service.application.auth.port.out.GenerateTokenPort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.application.auth.port.out.RefreshTokenHashPort;
import com.runiverse.running_service.application.auth.port.out.SaveRefreshTokenHashPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.vo.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OauthLoginHandler implements OauthLoginUsecase {

    private final ExchangeOauthCodePort exchangeOauthCodePort;
    private final OauthUserResolver oauthUserResolver;
    private final GenerateTokenPort generateTokenPort;
    private final RefreshTokenHashPort refreshTokenHashPort;
    private final SaveRefreshTokenHashPort saveRefreshTokenHashPort;
    private final CheckOnboardPort checkOnboardPort;

    @Override
    public OauthLoginResult handle(OauthLoginCommand command) {
        // 1. provider 검증
        Provider provider = resolveProvider(command.provider());

        // 2. 인가 코드 + verifier
        OauthProfile oauthProfile = exchangeOauthCodePort.exchange(
                provider,
                command.authorizationCode(),
                command.codeVerifier()
        );

        // 3. 조회 or 가입 (트랜잭션)
        User user = oauthUserResolver.findOrRegister(oauthProfile);

        // 4. jwt 토큰 생성
        String accessToken = generateTokenPort.generateAccessToken(user.getUserId());
        String refreshToken = generateTokenPort.generateRefreshToken(user.getUserId());

        // 5. refresh token 해시 후 저장
        saveRefreshTokenHashPort.save(user.getUserId(), refreshTokenHashPort.hash(refreshToken));

        // 6. 온보딩 완료 여부 조회
        boolean isOnboarded = checkOnboardPort.existsByUserId(user.getUserId());

        // 7. 반환
        return new OauthLoginResult(user.getUserId().value(), accessToken, refreshToken, isOnboarded);
    }

    private Provider resolveProvider(String value) {
        try {
            return Provider.from(value);
        } catch (com.runiverse.running_service.domain.user.exception.UnsupportedProviderException e) {
            throw new UnsupportedProviderException();
        }
    }
}
