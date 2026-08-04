package com.runiverse.running_service.integration_test;

import com.runiverse.running_service.integration_test.fake.*;
import org.junit.jupiter.api.BeforeEach;

public abstract class IntegrationTestSupport {
    protected InMemoryUserStore userStore;
    protected InMemoryRefreshTokenStore refreshTokenStore;
    protected InMemoryOnboardStore onboardStore;
    protected InMemoryAccessTokenBlacklist accessTokenBlacklist;
    protected FakePasswordHasher passwordHasher;
    protected FakeTokenProvider tokenProvider;
    protected FakeUserIdGenerator userIdGenerator;
    protected FakeOauthClient oauthClient;
    @BeforeEach
    void setUpFakes() {
        userStore = new InMemoryUserStore();
        refreshTokenStore = new InMemoryRefreshTokenStore();
        onboardStore = new InMemoryOnboardStore();
        accessTokenBlacklist = new InMemoryAccessTokenBlacklist();
        passwordHasher = new FakePasswordHasher();
        tokenProvider = new FakeTokenProvider();
        userIdGenerator = new FakeUserIdGenerator();
        oauthClient = new FakeOauthClient();
    }
}
