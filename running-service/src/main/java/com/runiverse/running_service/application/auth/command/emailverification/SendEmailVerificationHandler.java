package com.runiverse.running_service.application.auth.command.emailverification;

import com.runiverse.running_service.application.auth.port.in.SendEmailVerificationUsecase;
import com.runiverse.running_service.application.auth.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SendEmailVerificationHandler implements SendEmailVerificationUsecase {

    private final AcquireSendCooldownPort acquireSendCooldownPort;
    private final ReleaseSendCooldownPort releaseSendCooldownPort;
    private final CheckDailySendLimitPort checkDailySendLimitPort;
    private final GenerateVerificationCodePort generateVerificationCodePort;
    private final VerificationCodeHashPort verificationCodeHashPort;
    private final SaveVerificationCodePort saveVerificationCodePort;
    private final DeleteVerificationCodePort deleteVerificationCodePort;
    private final SendEmailPort sendEmailPort;

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
