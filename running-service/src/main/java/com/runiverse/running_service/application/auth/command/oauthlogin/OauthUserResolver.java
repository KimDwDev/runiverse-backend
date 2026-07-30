package com.runiverse.running_service.application.auth.command.oauthlogin;

import com.runiverse.running_service.application.auth.exception.EmailAlreadyExistsException;
import com.runiverse.running_service.application.auth.port.out.*;
import com.runiverse.running_service.domain.user.aggregate.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OauthUserResolver {

    private final LoadUserByProviderPort loadUserByProviderPort;
    private final CheckEmailDuplicatePort checkEmailDuplicatePort;
    private final GenerateUserIdPort generateUserIdPort;
    private final SaveUserPort saveUserPort;

    @Transactional
    public User findOrRegister(OauthProfile oauthProfile) {
        // 1. (provider, providerId)로 기존 유저 조회
        Optional<User> foundUser = loadUserByProviderPort.loadByProvider(
                oauthProfile.provider(),
                oauthProfile.providerId()
        );

        // 2. 있으면 그대로 반환
        if (foundUser.isPresent()) return foundUser.get();

        // 3. 없으면 가입
        return register(oauthProfile);
    }

    private User register(OauthProfile oauthProfile) {
        // 1. 기존 로컬 계정과 이메일이 겹치면 자동 연동하지 않는다
        if (checkEmailDuplicatePort.existsByEmail(oauthProfile.email())) {
            throw new EmailAlreadyExistsException();
        }

        // 2.UUIDv7 발급
        UUID userId = generateUserIdPort.generate();

        // 3. 유저 생성 + 소셜 연결
        User user = User.registerWithOauth(
                userId,
                oauthProfile.email(),
                oauthProfile.provider(),
                oauthProfile.providerId()
        );

        // 4. 저장
        return saveUserPort.save(user);
    }

}
