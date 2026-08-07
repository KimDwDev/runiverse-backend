package com.runiverse.running_service.integration_test.auth;

import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginCommand;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginHandler;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginResult;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthUserResolver;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.exception.EmailAlreadyExistsException;
import com.runiverse.running_service.application.auth.exception.OauthCodeExchangeFailedException;
import com.runiverse.running_service.application.auth.exception.UnsupportedProviderException;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("소셜 로그인 통합 테스트")
public class OauthLoginIntegrationTest extends IntegrationTestSupport {
    private static final String AUTH_CODE = "kakao-authorization-code";
    private static final String CODE_VERIFIER = "pkce-code-verifier";
    private static final String KAKAO_ID = "1234567890";
    private static final String KAKAO_EMAIL = "runner@kakao.com";
    private SignUpHandler signUpHandler;
    private OauthLoginHandler oauthLoginHandler;
    @BeforeEach
    void setUp() {
        signUpHandler = new SignUpHandler(
                verificationTicketHasher, verificationTicketStore,
                userStore, passwordHasher, userIdGenerator, userStore);
        OauthUserResolver oauthUserResolver = new OauthUserResolver(
                userStore,        // LoadUserByProviderPort
                userStore,        // CheckEmailDuplicatePort
                userIdGenerator,  // GenerateUserIdPort
                userStore         // SaveUserPort
        );
        oauthLoginHandler = new OauthLoginHandler(
                oauthClient,        // ExchangeOauthCodePort
                oauthUserResolver,
                tokenProvider,      // GenerateTokenPort
                tokenProvider,      // RefreshTokenHashPort
                refreshTokenStore,  // SaveRefreshTokenHashPort
                onboardStore        // CheckOnboardPort
        );
        oauthClient.register(AUTH_CODE, new OauthProfile(Provider.KAKAO, KAKAO_ID, KAKAO_EMAIL));
    }
    private OauthLoginResult login() {
        return oauthLoginHandler.handle(new OauthLoginCommand("kakao", AUTH_CODE, CODE_VERIFIER));
    }

    @Test
    @DisplayName("처음 소셜 로그인하면 유저가 새로 가입되고 토큰을 받는다")
    void firstOauthLoginRegistersUser() {
        // when
        OauthLoginResult result = login();
        // then
        assertThat(userStore.size()).isEqualTo(1);
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.isOnboarded()).isFalse();

        User saved = userStore.findById(result.userId()).orElseThrow();
        assertThat(saved.getEmail().value()).isEqualTo(KAKAO_EMAIL);
        assertThat(saved.hasProvider(Provider.KAKAO)).isTrue();
    }
    @Test
    @DisplayName("소셜 가입 유저는 비밀번호 없이 만들어진다")
    void oauthUserHasNoPassword() {
        // when
        OauthLoginResult result = login();
        // then
        User saved = userStore.findById(result.userId()).orElseThrow();
        assertThat(saved.getPasswordHash().value()).isEmpty();
    }
    @Test
    @DisplayName("같은 소셜 계정으로 다시 로그인하면 가입하지 않고 같은 userId를 돌려준다")
    void secondOauthLoginReusesUser() {
        // given
        OauthLoginResult first = login();
        // when
        OauthLoginResult second = login();
        // then
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(userStore.size()).isEqualTo(1);
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    }
    @Test
    @DisplayName("소셜 로그인도 refresh token을 해시로 저장한다")
    void oauthLoginStoresHashedRefreshToken() {
        // when
        OauthLoginResult result = login();
        // then
        String storedHash = refreshTokenStore.loadById(result.userId()).orElseThrow();
        assertThat(storedHash).isNotEqualTo(result.refreshToken());
        assertThat(tokenProvider.matches(result.refreshToken(), storedHash)).isTrue();
    }
    @Test
    @DisplayName("온보딩을 마친 소셜 유저는 isOnboarded가 true다")
    void oauthLoginReturnsOnboardedStatus() {
        // given
        OauthLoginResult first = login();
        onboardStore.markOnboarded(first.userId());
        // when
        OauthLoginResult second = login();
        // then
        assertThat(second.isOnboarded()).isTrue();
    }
    @Test
    @DisplayName("이미 로컬 가입된 이메일이면 자동 연동하지 않고 EmailAlreadyExistsException이 발생한다")
    void oauthLoginRejectsExistingLocalEmail() {
        // given - 같은 이메일로 로컬 회원가입이 되어 있다
        signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(KAKAO_EMAIL), "Password123!"));
        // when & then
        assertThatThrownBy(this::login)
                .isInstanceOf(EmailAlreadyExistsException.class);
        // 소셜 유저가 추가로 만들어지지 않는다
        assertThat(userStore.size()).isEqualTo(1);
        assertThat(refreshTokenStore.isEmpty()).isTrue();
    }
    @Test
    @DisplayName("지원하지 않는 provider면 UnsupportedProviderException이 발생하고 코드 교환을 시도하지 않는다")
    void oauthLoginWithUnsupportedProvider() {
        // when & then
        assertThatThrownBy(() -> oauthLoginHandler.handle(
                new OauthLoginCommand("naver", AUTH_CODE, CODE_VERIFIER)))
                .isInstanceOf(UnsupportedProviderException.class);
        // provider 검증이 먼저이므로 외부 호출이 일어나지 않는다
        assertThat(oauthClient.exchangeCount()).isZero();
        assertThat(userStore.size()).isZero();
    }
    @Test
    @DisplayName("provider 이름은 대소문자를 가리지 않는다")
    void providerIsCaseInsensitive() {
        // when
        OauthLoginResult result = oauthLoginHandler.handle(
                new OauthLoginCommand("KaKaO", AUTH_CODE, CODE_VERIFIER));
        // then
        assertThat(result.userId()).isNotNull();
        assertThat(userStore.size()).isEqualTo(1);
    }
    @Test
    @DisplayName("인가 코드 교환에 실패하면 OauthCodeExchangeFailedException이 발생하고 아무것도 저장되지 않는다")
    void oauthLoginWithInvalidAuthorizationCode() {
        // when & then
        assertThatThrownBy(() -> oauthLoginHandler.handle(
                new OauthLoginCommand("kakao", "expired-code", CODE_VERIFIER)))
                .isInstanceOf(OauthCodeExchangeFailedException.class);

        assertThat(userStore.size()).isZero();
        assertThat(refreshTokenStore.isEmpty()).isTrue();
    }
}
