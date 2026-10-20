package com.runiverse.running_service.infrastructure.persistence.device;

import com.runiverse.running_service.application.device.port.out.LoadAppVersionPolicyPort;
import com.runiverse.running_service.domain.device.AppVersionPolicy;
import com.runiverse.running_service.domain.device.vo.Platform;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AppVersionPersistenceAdapter implements LoadAppVersionPolicyPort {

    private final EntityManager entityManager;

    // 버전 검사 필터가 트랜잭션 없이 부른다 — getResultStream()은 트랜잭션 밖에서 커서가 먼저 닫혀 쓰지 않는다
    @Override
    public Optional<AppVersionPolicy> load(Platform platform) {
        return entityManager.createQuery("""
                        SELECT appVersion
                        FROM AppVersionJpaEntity appVersion
                        WHERE appVersion.platform = :platform
                        """, AppVersionJpaEntity.class)
                .setParameter("platform", platform)
                .getResultList().stream()
                .findFirst()
                .map(AppVersionPersistenceAdapter::toDomain);
    }

    private static AppVersionPolicy toDomain(AppVersionJpaEntity entity) {
        return AppVersionPolicy.builder()
                .platform(entity.getPlatform())
                .minimumVersion(entity.getMinimumVersion())
                .latestVersion(entity.getLatestVersion())
                .build();
    }
}
