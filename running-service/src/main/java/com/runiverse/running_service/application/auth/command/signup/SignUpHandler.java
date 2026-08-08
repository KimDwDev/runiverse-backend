package com.runiverse.running_service.application.auth.command.signup;

import com.runiverse.running_service.application.auth.exception.EmailNotVerifiedException;
import com.runiverse.running_service.application.auth.port.in.SignUpUsecase;
import com.runiverse.running_service.application.auth.port.out.*;
import com.runiverse.running_service.domain.user.aggregate.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class SignUpHandler implements SignUpUsecase {

    private final VerificationTicketHashPort verificationTicketHashPort;
    private final ConsumeVerificationTicketPort consumeVerificationTicketPort;
    private final SignUpUserRegistrar signUpUserRegistrar;
    private final GenerateTokenPort generateTokenPort;
    private final RefreshTokenHashPort refreshTokenHashPort;
    private final SaveRefreshTokenHashPort saveRefreshTokenHashPort;

    @Override
    public SignUpResult handle(SignUpCommand command) {
        // 1. 티켓을 소비해 이메일을 얻는다. 요청이 보낸 이메일은 애초에 받지 않는다
        String hashedTicket = verificationTicketHashPort.hash(command.verificationTicket());
        String email = consumeVerificationTicketPort.consume(hashedTicket);
        if (email == null) throw new EmailNotVerifiedException();

        // 2. 유저 생성
        User user = signUpUserRegistrar.register(email, command.password());

        // 3. token 생성
        String accessToken = generateTokenPort.generateAccessToken(user.getUserId());
        String refreshToken = generateTokenPort.generateRefreshToken(user.getUserId());

        // 4. refresh token해시화 후 refresh token redis 저장
        saveRefreshTokenHashPort.save(user.getUserId(), refreshTokenHashPort.hash(refreshToken));

        // 5. 결과 반환 (방금 회원가입 했으니 온보딩 여부는 항상 false)
        return new SignUpResult(user.getUserId().value(), accessToken, refreshToken, false);
    }

}
