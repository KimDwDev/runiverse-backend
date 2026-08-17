package com.runiverse.running_service.application.user.command.password;

import com.runiverse.running_service.application.common.port.out.PasswordHashPort;
import com.runiverse.running_service.application.user.exception.InvalidCurrentPasswordException;
import com.runiverse.running_service.application.user.exception.PasswordNotSetException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.ChangePasswordUsecase;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.UpdatePasswordPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChangePasswordHandler implements ChangePasswordUsecase {

    private final LoadUserByIdPort loadUserByIdPort;
    private final PasswordHashPort passwordHashPort;
    private final UpdatePasswordPort updatePasswordPort;

    @Override
    public void handle(ChangePasswordCommand command) {
        UserId userId = new UserId(command.userId());
        // 1. 유저 조회
        User user = loadUserByIdPort.loadById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 2. 소설 전용 계정은 바꾸지 않는다
        if (user.isPasswordNotSet()) {
            throw new PasswordNotSetException();
        }

        // 3. 현재 비밀번호 확인
        if (!passwordHashPort.matches(command.currentPassword(), user.getPasswordHash().value())) {
            throw new InvalidCurrentPasswordException();
        }

        // 4. 도메인에서 빈 해시 검증
        user.changePassword(passwordHashPort.hash(command.newPassword()));

        // 5. 갱신
        updatePasswordPort.updatePassword(userId, user.getPasswordHash());
    }
}
