package com.runiverse.running_service.application.user.command.login;

import com.runiverse.running_service.application.user.exception.InvalidEmailCredentialsException;
import com.runiverse.running_service.application.user.exception.InvalidPasswordCredentialsException;
import com.runiverse.running_service.application.user.port.in.LoginUsecase;
import com.runiverse.running_service.application.user.port.out.GenerateTokenPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByEmailPort;
import com.runiverse.running_service.application.user.port.out.PasswordHashPort;
import com.runiverse.running_service.application.user.port.out.SaveRefreshTokenPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginHandler implements LoginUsecase {

    private final LoadUserByEmailPort loadUserByEmailPort;
    private final PasswordHashPort passwordHashPort;
    private final GenerateTokenPort generateTokenPort;
    private final SaveRefreshTokenPort saveRefreshTokenPort;

    @Override
    public LoginResult handle(LoginCommand command) {

        // 1. 이메일 확인
        Optional<User> foundUser = loadUserByEmailPort.loadByEmail(command.email());
        if (foundUser.isEmpty()) throw new InvalidEmailCredentialsException();
        User user = foundUser.get();

        // 2. 비밀번호 확인
        boolean passwordChecked = passwordHashPort.matches(
                command.password(),
                user.getPasswordHash().value());
        if (!passwordChecked) throw new InvalidPasswordCredentialsException();

        // 3. jwt 토큰 생성
        String accessToken = generateTokenPort.generateAccessToken(user.getUserId());
        String refreshToken = generateTokenPort.generateRefreshToken(user.getUserId());

        // 4. refresh token redis 저장
        saveRefreshTokenPort.save(user.getUserId(), refreshToken);

        // 5. 반환
        return new LoginResult(user.getUserId().value(), accessToken, refreshToken);
    }
}
