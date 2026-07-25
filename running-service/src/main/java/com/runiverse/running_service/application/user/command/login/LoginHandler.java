package com.runiverse.running_service.application.user.command.login;

import com.runiverse.running_service.application.user.port.in.LoginUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginHandler implements LoginUsecase {

    @Override
    public LoginResult handle(LoginCommand command) {

        // 1. 이메일 확인

        // 2. 비밀번호 확인

        // 3. jwt 토큰 생성

        // 4. refresh token redis 저장

        // 5. 반환
        return null;
    }
}
