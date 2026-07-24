package com.runiverse.running_service.application.user.command.signup;

import com.runiverse.running_service.application.user.port.in.SignUpUsecase;
import com.runiverse.running_service.application.user.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.user.port.out.GenerateUserIdPort;
import com.runiverse.running_service.application.user.port.out.PasswordHashPort;
import com.runiverse.running_service.application.user.port.out.SaveUserPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.sun.jdi.request.DuplicateRequestException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SignUpHandler implements SignUpUsecase {

    private final CheckEmailDuplicatePort checkEmailDuplicatePort;
    private final PasswordHashPort passwordHashPort;
    private final GenerateUserIdPort generateUserIdPort;
    private final SaveUserPort saveUserPort;

    @Override
    public SignUpResult handle(SignUpCommand command) {
        // 1. 이메일 중복 확인
        boolean emailExists = checkEmailDuplicatePort.existsByEmail(command.email());
        if (emailExists) throw new DuplicateRequestException("이메일이 존재합니다.");

        // 2. 비밀번호 해시화
        String hashedPassword = passwordHashPort.hash(command.password());

        // 3. UUIDv7 생성
        UUID userId = generateUserIdPort.generate();

        // 4. 도메인 User 생성
        User user = new User(userId, command.email(), hashedPassword);

        // 5. DB 저장
        User savedUser = saveUserPort.save(user);

        // 6. 결과 반환
        return new SignUpResult(savedUser.getUserId().value());
    }

}
