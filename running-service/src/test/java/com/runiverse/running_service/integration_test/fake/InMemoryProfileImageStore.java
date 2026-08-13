package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.user.port.out.UpdateProfileImagePort;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.domain.user.vo.UserId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryProfileImageStore implements UpdateProfileImagePort {

    private final Map<UUID, String> saved = new HashMap<>();

    @Override
    public void updateProfileImage(UserId userId, ProfileImageKey profileImageKey) {
        saved.put(userId.value(), profileImageKey.value());
    }

    // 아래는 검증 전용
    public Optional<String> keyOf(UUID userId) {
        return Optional.ofNullable(saved.get(userId));
    }

    public boolean isEmpty() {
        return saved.isEmpty();
    }
}
