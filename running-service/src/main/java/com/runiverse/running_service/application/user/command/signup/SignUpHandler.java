package com.runiverse.running_service.application.user.command.signup;

import com.runiverse.running_service.application.user.port.in.SignUpUsecase;
import com.runiverse.running_service.application.user.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.user.port.out.GenerateUserIdPort;
import com.runiverse.running_service.application.user.port.out.PasswordHashPort;
import com.runiverse.running_service.application.user.port.out.SaveUserPort;
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
        System.out.println("작동중");

        // 1. 이메일 중복 확인
        // 2. 비밀번호 해시화
        // 3. UUIDv7 생성
        // 4. 도메인 User 생성
        // 5. DB 저장
        // 6. 결과 반환

        UUID userId = UUID.randomUUID();

        return new SignUpResult(userId);
    }

}
