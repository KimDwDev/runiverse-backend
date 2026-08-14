package com.runiverse.running_service.application.user.command.nickname;

import com.runiverse.running_service.application.user.port.in.ChangeNicknameUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChangeNicknameHandler implements ChangeNicknameUsecase {


    @Override
    public ChangeNicknameResult handle(ChangeNicknameCommand command) {

        // 1. vo에서 userId에 대한 정규화 진행

        // 2. 온보딩에 존재하는지 우선적으로 확인

        // 3. 자기 자신의 닉네임과 같으면 그대로 반환

        // 4. 남이 닉네임을 쓰고 있으면 막는다

        // 5. 갱신
        return null;
    }
}
