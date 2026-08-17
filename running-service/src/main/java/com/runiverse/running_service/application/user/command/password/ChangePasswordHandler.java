package com.runiverse.running_service.application.user.command.password;

import com.runiverse.running_service.application.user.port.in.ChangePasswordUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChangePasswordHandler implements ChangePasswordUsecase {

    @Override
    public void handle(ChangePasswordCommand command) {
        // 1. 유저 조회

        // 2. 소설 전용 계정은 바꾸지 않는다

        // 3. 현재 비밀번호 확인

        // 4. 도메인에서 빈 해시 검증

        // 5. 갱신
    }
}
