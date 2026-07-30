package com.runiverse.running_service.application.auth.command.oauthlogin;

import com.runiverse.running_service.application.auth.port.out.LoadUserByProviderPort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.aggregate.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OauthUserResolver {

    @Transactional
    public User findOrRegister(OauthProfile oauthProfile) {
        // 1. (provider, providerId)로 기존 유저 조회

        // 2. 있으면 그대로 반환

        // 3. 없으면 가입
        return null;
    }

    public User register(OauthProfile oauthProfile) {
        // 1. 기존 로컬 계정과 이메일이 겹치면 자동 연동하지 않는다

        // 2.UUIDv7 발급

        // 3. 유저 생성 + 소셜 연결

        // 4. 저장
        return null;
    }

}
