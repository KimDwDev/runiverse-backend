package com.runiverse.running_service.application.user.command.onboard;

import com.runiverse.running_service.application.user.exception.AlreadyOnboardException;
import com.runiverse.running_service.application.user.exception.NicknameAlreadyExistsException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.CompleteOnboardUsecase;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.ExistsOnboardPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.SaveOnboardPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.aggregate.UserOnboard;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CompleteOnboardHandler implements CompleteOnboardUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final ExistsOnboardPort existsOnboardPort;
    private final CheckNicknameDuplicatePort checkNicknameDuplicatePort;
    private final SaveOnboardPort saveOnboardPort;

    @Override
    public CompleteOnboardResult handle(CompleteOnboardCommand command) {
        // 1. 유저 조회
        UserId userId = new UserId(command.userId());
        User user = loadUserByIdPort.loadById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 2. 온보딩 되어 있으면 막는다
        if (existsOnboardPort.existsByUserId(userId)) {
            throw new AlreadyOnboardException();
        }

        // 3. 닉네임 정규화 -> 중복 검사 확인
        Nickname nickname = new Nickname(command.nickname());

        // 4. 닉네임 중복 확인
        if (checkNicknameDuplicatePort.existsByNickname(nickname)) {
            throw new NicknameAlreadyExistsException();
        }

        // 5. 도메인에 위임
        user.completeOnboarding(
                nickname.value(),
                command.gender(),
                command.birthday(),
                command.avgPace(),
                command.weight(),
                command.height()
        );

        // 6. 저장
        UserOnboard onboard = user.getOnboard().orElseThrow();
        saveOnboardPort.saveOnboard(onboard);

        // 7. 반환
        return new CompleteOnboardResult(user.getUserId().value(), onboard.getNickname().value());
    }

}
