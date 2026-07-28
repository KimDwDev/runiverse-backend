package com.runiverse.running_service.application.auth.command.reissue;

import com.runiverse.running_service.application.auth.port.in.ReissueUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReissueHandler implements ReissueUsecase {

    @Override
    public ReissueResult handle(ReissueCommand command) {

        // 1. refresh token 검증 후 소유자 확인 (서명, 만료, issuer, audience)

        // 2. 저장된 해시 조회

        // 3. 대조 — 불일치 시 탈취로 보고 폐기

        // 3. 새 access token, refresh token 발급 (로테이션)

        // 4. 새 refresh token 지문 저장

        // 5. 반환
        return null;
    }
}
