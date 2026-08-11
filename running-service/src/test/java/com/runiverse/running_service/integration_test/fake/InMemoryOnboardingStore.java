package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.CheckOnboardingPort;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.ExistsOnboardingPort;
import com.runiverse.running_service.application.user.port.out.SaveOnboardingPort;
import com.runiverse.running_service.domain.user.aggregate.UserOnboarding;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.UserId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class InMemoryOnboardingStore implements CheckOnboardingPort, ExistsOnboardingPort,
        CheckNicknameDuplicatePort, SaveOnboardingPort {

    private final Map<UUID, UserOnboarding> onboardings = new LinkedHashMap<>();
    // 온보딩 유즈케이스를 거치지 않고 상태만 심은 유저까지 포함한다
    private final Set<UUID> onboardedUserIds = new HashSet<>();

    @Override
    public void saveOnboarding(UserOnboarding onboarding) {
        UUID userId = onboarding.getUserId().value();
        onboardings.put(userId, onboarding);
        onboardedUserIds.add(userId);
    }

    // CheckOnboardingPort(auth) + ExistsOnboardingPort(user) 두 포트를 함께 만족한다
    @Override
    public boolean existsByUserId(UserId userId) {
        return onboardedUserIds.contains(userId.value());
    }

    @Override
    public boolean existsByNickname(Nickname nickname) {
        return onboardings.values().stream()
                .anyMatch(onboarding -> onboarding.getNickname().equals(nickname));
    }

    // 테스트 준비
    public void markOnboarded(UUID userId) {
        onboardedUserIds.add(userId);
    }

    // 검증 전용
    public Optional<UserOnboarding> findByUserId(UUID userId) {
        return Optional.ofNullable(onboardings.get(userId));
    }

    public int size() {
        return onboardings.size();
    }
}
