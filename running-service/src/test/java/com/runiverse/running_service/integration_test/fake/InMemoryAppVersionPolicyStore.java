package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.device.port.out.LoadAppVersionPolicyPort;
import com.runiverse.running_service.domain.device.AppVersionPolicy;
import com.runiverse.running_service.domain.device.vo.Platform;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

// AppVersionPersistenceAdapter를 대신한다 — 운영이 SQL로 행을 넣고 바꾸는 것을 register로 흉내 낸다
public class InMemoryAppVersionPolicyStore implements LoadAppVersionPolicyPort {

    private final Map<Platform, AppVersionPolicy> policies = new EnumMap<>(Platform.class);

    public void register(Platform platform, String minimumVersion, String latestVersion) {
        policies.put(platform, AppVersionPolicy.builder()
                .platform(platform)
                .minimumVersion(minimumVersion)
                .latestVersion(latestVersion)
                .build());
    }

    @Override
    public Optional<AppVersionPolicy> load(Platform platform) {
        return Optional.ofNullable(policies.get(platform));
    }
}
