package com.runiverse.running_service.application.auth.command.emailverification;

import com.runiverse.running_service.application.auth.port.in.VerifyEmailCodeUsecase;

public class VerifyEmailCodeHandler implements VerifyEmailCodeUsecase {

    @Override
    public VerifyEmailCodeResult handle(VerifyEmailCodeCommand command) {
        // 1. Email vo 정규화

        // 2. 시도 횟수 1 소비 -> 인증코드 확인 -> 인증 코드 삭제 -> 상태 반환

        // 3. 티켓 생성 -> 해시

        // 4. 티켓 저장

        // 5. 원본 티켓 반환
        return null;
    }
}
