package com.runiverse.running_service.integration_test.auth;

import com.runiverse.running_service.application.auth.command.login.LoginCommand;
import com.runiverse.running_service.application.auth.command.login.LoginHandler;
import com.runiverse.running_service.application.auth.command.login.LoginResult;
import com.runiverse.running_service.application.auth.command.reissue.ReissueCommand;
import com.runiverse.running_service.application.auth.command.reissue.ReissueHandler;
import com.runiverse.running_service.application.auth.command.reissue.ReissueResult;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.exception.InvalidRefreshTokenException;
import com.runiverse.running_service.domain.user.vo.UserId;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@DisplayName("토큰 재발급 통합 테스트")
public class ReissueIntegrationTest extends IntegrationTestSupport {
    private static final String EMAIL = "runner@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private SignUpHandler signUpHandler;
    private LoginHandler loginHandler;
    private ReissueHandler reissueHandler;
    @BeforeEach
    void setUp() {
        signUpHandler = new SignUpHandler(
                verificationTicketHasher, verificationTicketStore,
                userStore, passwordHasher, userIdGenerator, userStore);
        loginHandler = new LoginHandler(
                userStore, passwordHasher, tokenProvider,
                tokenProvider, refreshTokenStore, onboardStore);
        reissueHandler = new ReissueHandler(
                tokenProvider,      // ParseRefreshTokenPort
                refreshTokenStore,  // LoadRefreshTokenPort
                tokenProvider,      // RefreshTokenHashPort
                refreshTokenStore,  // DeleteRefreshTokenPort
                tokenProvider,      // GenerateTokenPort
                refreshTokenStore   // SaveRefreshTokenHashPort
        );
    }
    // 가입 -> 로그인까지 마친 상태를 만든다
    private LoginResult signUpAndLogin() {
        signUpHandler.handle(new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD));
        return loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
    }

    @Test
    @DisplayName("발급받은 refresh token으로 재발급하면 새 토큰 두 개를 받는다")
    void reissueSuccess() {
        // given
        LoginResult login = signUpAndLogin();
        // when
        ReissueResult result = reissueHandler.handle(new ReissueCommand(login.refreshToken()));
        // then
        assertThat(result.accessToken()).isNotBlank().isNotEqualTo(login.accessToken());
        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo(login.refreshToken());
    }
    @Test
    @DisplayName("재발급하면 저장된 해시가 새 refresh token의 것으로 교체된다")
    void reissueReplacesStoredHash() {
        // given
        LoginResult login = signUpAndLogin();
        String beforeHash = refreshTokenStore.loadById(login.userId()).orElseThrow();
        // when
        ReissueResult result = reissueHandler.handle(new ReissueCommand(login.refreshToken()));
        // then
        String afterHash = refreshTokenStore.loadById(login.userId()).orElseThrow();
        assertThat(afterHash).isNotEqualTo(beforeHash);
        assertThat(tokenProvider.matches(result.refreshToken(), afterHash)).isTrue();
    }
    @Test
    @DisplayName("이미 사용한 refresh token을 다시 쓰면 탈취로 보고 저장된 토큰을 폐기한다")
    void reusingOldRefreshTokenRevokesEverything() {
        // given
        LoginResult login = signUpAndLogin();
        reissueHandler.handle(new ReissueCommand(login.refreshToken()));
        // when & then
        assertThatThrownBy(() -> reissueHandler.handle(new ReissueCommand(login.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
        // 정상 발급된 최신 토큰까지 함께 무효화된다
        assertThat(refreshTokenStore.loadById(login.userId())).isEmpty();
    }
    @Test
    @DisplayName("재발급받은 토큰으로 연속해서 재발급할 수 있다")
    void reissueChain() {
        // given
        LoginResult login = signUpAndLogin();
        // when
        ReissueResult first = reissueHandler.handle(new ReissueCommand(login.refreshToken()));
        ReissueResult second = reissueHandler.handle(new ReissueCommand(first.refreshToken()));
        // then
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        String storedHash = refreshTokenStore.loadById(login.userId()).orElseThrow();
        assertThat(tokenProvider.matches(second.refreshToken(), storedHash)).isTrue();
    }
    @Test
    @DisplayName("형식이 잘못된 토큰이면 InvalidRefreshTokenException이 발생한다")
    void reissueWithMalformedToken() {
        // given
        LoginResult login = signUpAndLogin();
        // when & then
        assertThatThrownBy(() -> reissueHandler.handle(new ReissueCommand("not-a-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
        // 남의 토큰이 아니므로 저장된 토큰은 그대로 살아 있다
        assertThat(refreshTokenStore.loadById(login.userId())).isPresent();
    }
    @Test
    @DisplayName("access token을 refresh token 자리에 넘기면 InvalidRefreshTokenException이 발생한다")
    void reissueWithAccessToken() {
        // given
        LoginResult login = signUpAndLogin();
        // when & then
        assertThatThrownBy(() -> reissueHandler.handle(new ReissueCommand(login.accessToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
    @Test
    @DisplayName("저장소에 토큰이 없는 유저면 InvalidRefreshTokenException이 발생한다")
    void reissueWithoutStoredToken() {
        // given - 로그인하지 않아 저장된 적 없는 유저의 토큰
        UUID userId = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD)).userId();
        String neverStored = tokenProvider.generateRefreshToken(new UserId(userId));
        // when & then
        assertThatThrownBy(() -> reissueHandler.handle(new ReissueCommand(neverStored)))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
