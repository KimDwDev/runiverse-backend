package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.CheckOnboardPort;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.ExistsOnboardPort;
import com.runiverse.running_service.application.user.port.out.SaveOnboardPort;
import com.runiverse.running_service.domain.user.aggregate.UserOnboard;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.UserId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class InMemoryOnboardStore implements CheckOnboardPort, ExistsOnboardPort,
        CheckNicknameDuplicatePort, SaveOnboardPort {

    private final Map<UUID, UserOnboard> onboards = new LinkedHashMap<>();
    // 온보딩 유즈케이스를 거치지 않고 상태만 심은 유저까지 포함한다
    private final Set<UUID> onboardedUserIds = new HashSet<>();

    @Override
    public void saveOnboard(UserOnboard onboard) {
        UUID userId = onboard.getUserId().value();
        onboards.put(userId, onboard);
        onboardedUserIds.add(userId);
    }

    // CheckOnboardPort(auth) + ExistsOnboardPort(user) 두 포트를 함께 만족한다
    @Override
    public boolean existsByUserId(UserId userId) {
        return onboardedUserIds.contains(userId.value());
    }

    @Override
    public boolean existsByNickname(Nickname nickname) {
        return onboards.values().stream()
                .anyMatch(onboard -> onboard.getNickname().equals(nickname));
    }

    // 테스트 준비
    public void markOnboarded(UUID userId) {
        onboardedUserIds.add(userId);
    }

    // 검증 전용
    public Optional<UserOnboard> findByUserId(UUID userId) {
        return Optional.ofNullable(onboards.get(userId));
    }

    public int size() {
        return onboards.size();
    }
}
