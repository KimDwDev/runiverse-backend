package com.runiverse.running_service.application.auth.command.emailverification;

import com.runiverse.running_service.application.auth.port.in.SendEmailVerificationUsecase;

public class SendEmailVerificationHandler implements SendEmailVerificationUsecase {

    @Override
    public void handle(SendEmailVerificationCommand command) {
        // 1. 이메일 정규화

        // 2. 재발송 쿨다운 선점

        // 3. 전송 횟수 제한 확인

        // 4. email code를 생성한다.

        // 5. email code를 해시화 한다.

        // 6. email hash code 저장

        // 7. email code 전송
    }
}
