package com.runiverse.running_service.integration_test.auth;

import com.runiverse.running_service.application.auth.command.login.LoginCommand;
import com.runiverse.running_service.application.auth.command.login.LoginHandler;
import com.runiverse.running_service.application.auth.command.login.LoginResult;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.exception.InvalidCredentialsException;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@DisplayName("로그인 통합 테스트")
public class LoginIntegrationTest extends IntegrationTestSupport {
    private static final String EMAIL = "runner@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private SignUpHandler signUpHandler;
    private LoginHandler loginHandler;
    @BeforeEach
    void setUp() {
        signUpHandler = new SignUpHandler(
                verificationTicketHasher, verificationTicketStore,
                userStore, passwordHasher, userIdGenerator, userStore);
        loginHandler = new LoginHandler(
                userStore,
                passwordHasher,
                tokenProvider,
                tokenProvider,
                refreshTokenStore,
                onboardStore
        );
    }
    private UUID signUp() {
        return signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD)).userId();
    }

    @Test
    @DisplayName("가입한 계정으로 로그인하면 가입 때와 같은 userId와 토큰 두 개를 받는다")
    void loginSuccess() {
        // given
        UUID userId = signUp();
        // when
        LoginResult result = loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.accessToken()).isNotEqualTo(result.refreshToken());
    }
    @Test
    @DisplayName("refresh token은 원문이 아니라 해시로 저장된다")
    void loginStoresHashedRefreshToken() {
        // given
        UUID userId = signUp();
        // when
        LoginResult result = loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        // then
        String storedHash = refreshTokenStore.loadById(userId).orElseThrow();
        assertThat(storedHash).isNotEqualTo(result.refreshToken());
        assertThat(tokenProvider.matches(result.refreshToken(), storedHash)).isTrue();
    }
    @Test
    @DisplayName("가입하지 않은 이메일로 로그인하면 InvalidCredentialsException이 발생한다")
    void loginWithUnknownEmail() {
        assertThatThrownBy(() -> loginHandler.handle(new LoginCommand(EMAIL, PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(refreshTokenStore.isEmpty()).isTrue();
    }
    @Test
    @DisplayName("비밀번호가 틀리면 이메일이 없을 때와 같은 예외가 발생한다")
    void loginWithWrongPassword() {
        // given
        signUp();
        // when & then — 예외를 구분하면 이메일 존재 여부가 새어나간다
        assertThatThrownBy(() -> loginHandler.handle(new LoginCommand(EMAIL, "WrongPassword1!")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(refreshTokenStore.isEmpty()).isTrue();
    }
    @Test
    @DisplayName("가입 직후 로그인하면 isOnboarded가 false다")
    void loginBeforeOnboarding() {
        // given
        signUp();
        // when
        LoginResult result = loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        // then
        assertThat(result.isOnboarded()).isFalse();
    }
    @Test
    @DisplayName("온보딩을 마친 유저가 로그인하면 isOnboarded가 true다")
    void loginAfterOnboarding() {
        // given
        UUID userId = signUp();
        onboardStore.markOnboarded(userId);
        // when
        LoginResult result = loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        // then
        assertThat(result.isOnboarded()).isTrue();
    }
    @Test
    @DisplayName("재로그인하면 새 refresh token이 발급되고 저장된 해시가 교체된다")
    void reLoginReplacesStoredRefreshToken() {
        // given
        UUID userId = signUp();
        LoginResult first = loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        String firstHash = refreshTokenStore.loadById(userId).orElseThrow();
        // when
        LoginResult second = loginHandler.handle(new LoginCommand(EMAIL, PASSWORD));
        // then
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        String secondHash = refreshTokenStore.loadById(userId).orElseThrow();
        assertThat(secondHash).isNotEqualTo(firstHash);
        assertThat(tokenProvider.matches(first.refreshToken(), secondHash)).isFalse();
    }
}
