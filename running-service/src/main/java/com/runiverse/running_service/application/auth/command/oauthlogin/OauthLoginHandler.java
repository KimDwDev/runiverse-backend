package com.runiverse.running_service.application.auth.command.oauthlogin;

import com.runiverse.running_service.application.auth.port.in.OauthLoginUsecase;
import com.runiverse.running_service.domain.user.vo.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OauthLoginHandler implements OauthLoginUsecase {

    @Override
    public OauthLoginResult handle(OauthLoginCommand command) {
        // 1. provider 검증
        Provider provider = Provider.from(command.provider());

        // 2. 인가 코드 + verifier

        // 3. 조회 or 가입 (트랜잭션)

        // 4. jwt 토큰 생성

        // 5. refresh token 해시 후 저장

        // 6. 반환
        return null;
    }

}
