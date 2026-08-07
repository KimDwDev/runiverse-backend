package com.runiverse.running_service.application.auth.command.signup;

import com.runiverse.running_service.application.auth.exception.EmailAlreadyExistsException;
import com.runiverse.running_service.application.auth.exception.EmailNotVerifiedException;
import com.runiverse.running_service.application.auth.port.in.SignUpUsecase;
import com.runiverse.running_service.application.auth.port.out.*;
import com.runiverse.running_service.domain.user.aggregate.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SignUpHandler implements SignUpUsecase {

    private final VerificationTicketHashPort verificationTicketHashPort;
    private final ConsumeVerificationTicketPort consumeVerificationTicketPort;
    private final CheckEmailDuplicatePort checkEmailDuplicatePort;
    private final PasswordHashPort passwordHashPort;
    private final GenerateUserIdPort generateUserIdPort;
    private final SaveUserPort saveUserPort;

    @Override
    public SignUpResult handle(SignUpCommand command) {
        // 1. 티켓을 소비해 이메일을 얻는다. 요청이 보낸 이메일은 애초에 받지 않는다
        String hashedTicket = verificationTicketHashPort.hash(command.verificationTicket());
        String email = consumeVerificationTicketPort.consume(hashedTicket);
        if (email == null) throw new EmailNotVerifiedException();

        // 2. 이메일 중복 확인
        boolean emailExists = checkEmailDuplicatePort.existsByEmail(email);
        if (emailExists) throw new EmailAlreadyExistsException();

        // 3. 비밀번호 해시화
        String hashedPassword = passwordHashPort.hash(command.password());

        // 4. UUIDv7 생성
        UUID userId = generateUserIdPort.generate();

        // 5. 도메인 User 생성
        User user = new User(userId, email, hashedPassword);

        // 6. DB 저장
        User savedUser = saveUserPort.save(user);

        // 7. token 생성

        // 8. refresh token 서명화

        // 9. refresh token redis 저장

        // 10. 결과 반환
        return new SignUpResult(savedUser.getUserId().value());
    }

}
