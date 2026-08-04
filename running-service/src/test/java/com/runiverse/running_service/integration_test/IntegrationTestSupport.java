package com.runiverse.running_service.integration_test;

import com.runiverse.running_service.integration_test.fake.*;
import org.junit.jupiter.api.BeforeEach;

public class IntegrationTestSupport {
    protected InMemoryUserStore userStore;
    protected InMemoryRefreshTokenStore refreshTokenStore;
    protected InMemoryOnboardStore onboardStore;
    protected FakePasswordHasher passwordHasher;
    protected FakeTokenProvider tokenProvider;
    protected FakeUserIdGenerator userIdGenerator;
    @BeforeEach
    void setUpFakes() {
        userStore = new InMemoryUserStore();
        refreshTokenStore = new InMemoryRefreshTokenStore();
        onboardStore = new InMemoryOnboardStore();
        passwordHasher = new FakePasswordHasher();
        tokenProvider = new FakeTokenProvider();
        userIdGenerator = new FakeUserIdGenerator();
    }
}
