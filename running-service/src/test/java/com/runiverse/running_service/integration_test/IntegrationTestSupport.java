package com.runiverse.running_service.integration_test;

import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.command.signup.SignUpUserRegistrar;
import com.runiverse.running_service.integration_test.fake.*;
import org.junit.jupiter.api.BeforeEach;

public abstract class IntegrationTestSupport {
    protected InMemoryUserStore userStore;
    protected InMemoryRefreshTokenStore refreshTokenStore;
    protected InMemoryOnboardStore onboardStore;
    protected InMemoryAccessTokenBlacklist accessTokenBlacklist;
    protected InMemoryVerificationTicketStore verificationTicketStore;
    protected FakePasswordHasher passwordHasher;
    protected FakeTokenProvider tokenProvider;
    protected FakeUserIdGenerator userIdGenerator;
    protected FakeOauthClient oauthClient;
    protected FakeVerificationTicketHasher verificationTicketHasher;
    @BeforeEach
    void setUpFakes() {
        userStore = new InMemoryUserStore();
        refreshTokenStore = new InMemoryRefreshTokenStore();
        onboardStore = new InMemoryOnboardStore();
        accessTokenBlacklist = new InMemoryAccessTokenBlacklist();
        verificationTicketStore = new InMemoryVerificationTicketStore();
        passwordHasher = new FakePasswordHasher();
        tokenProvider = new FakeTokenProvider();
        userIdGenerator = new FakeUserIdGenerator();
        oauthClient = new FakeOauthClient();
        verificationTicketHasher = new FakeVerificationTicketHasher();
    }
    // 조립할 fake가 많고 여러 테스트가 가입부터 시작하므로 여기서 한 번만 엮는다
    protected SignUpHandler newSignUpHandler() {
        SignUpUserRegistrar registrar = new SignUpUserRegistrar(
                userStore,        // CheckEmailDuplicatePort
                passwordHasher,   // PasswordHashPort
                userIdGenerator,  // GenerateUserIdPort
                userStore         // SaveUserPort
        );
        return new SignUpHandler(
                verificationTicketHasher,  // VerificationTicketHashPort
                verificationTicketStore,   // ConsumeVerificationTicketPort
                registrar,
                tokenProvider,             // GenerateTokenPort
                tokenProvider,             // RefreshTokenHashPort
                refreshTokenStore          // SaveRefreshTokenHashPort
        );
    }
    // 이메일 인증을 마친 상태를 만들고 원문 티켓을 돌려준다.
    // 회원가입은 이 티켓으로만 이메일을 얻으므로 가입 전에 반드시 필요하다
    protected String issueVerificationTicket(String email) {
        String ticket = "verification-ticket-" + email;
        verificationTicketStore.save(verificationTicketHasher.hash(ticket), email);
        return ticket;
    }
}
