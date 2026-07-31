package com.runiverse.running_service.application.user.command.onboard;

import com.runiverse.running_service.application.user.port.in.CompleteOnboardUsecase;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.SaveOnboardPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CompleteOnboardHandler implements CompleteOnboardUsecase {

    @Override
    public CompleteOnboardResult handle(CompleteOnboardCommand command) {
        // 1. 유저 조회

        // 2. 닉네임 정규화 -> 중복 검사 확인

        // 3. 닉네임 중복 확인

        // 4. 도메인에 위임

        // 5. 저장

        // 6. 반환
        return null;
    }

}
