package com.runiverse.running_service.application.auth.command.login;

import com.runiverse.running_service.application.auth.exception.InvalidCredentialsException;
import com.runiverse.running_service.application.auth.port.in.LoginUsecase;
import com.runiverse.running_service.application.auth.port.out.*;
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
    private final RefreshTokenHashPort refreshTokenHashPort;
    private final SaveRefreshTokenHashPort saveRefreshTokenPort;

    @Override
    public LoginResult handle(LoginCommand command) {

        // 1. 이메일 확인
        Optional<User> foundUser = loadUserByEmailPort.loadByEmail(command.email());
        if (foundUser.isEmpty()) throw new InvalidCredentialsException();
        User user = foundUser.get();

        // 2. 비밀번호 확인
        boolean passwordChecked = passwordHashPort.matches(
                command.password(),
                user.getPasswordHash().value());
        if (!passwordChecked) throw new InvalidCredentialsException();

        // 3. jwt 토큰 생성
        String accessToken = generateTokenPort.generateAccessToken(user.getUserId());
        String refreshToken = generateTokenPort.generateRefreshToken(user.getUserId());

        // 4. refresh token 서명화
        String hashedRefreshToken = refreshTokenHashPort.hash(refreshToken);

        // 5. refresh token redis 저장
        saveRefreshTokenPort.save(user.getUserId(), hashedRefreshToken);

        // 6. 반환
        return new LoginResult(user.getUserId().value(), accessToken, refreshToken);
    }
}
