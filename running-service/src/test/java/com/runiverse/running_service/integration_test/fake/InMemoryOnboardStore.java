package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.CheckOnboardPort;
import com.runiverse.running_service.domain.user.vo.UserId;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class InMemoryOnboardStore implements CheckOnboardPort {
    private final Set<UUID> onboardedUserIds = new HashSet<>();
    @Override
    public boolean existsByUserId(UserId userId) {
        return onboardedUserIds.contains(userId.value());
    }
    // 테스트 준비
    public void markOnboarded(UUID userId) {
        onboardedUserIds.add(userId);
    }
}
