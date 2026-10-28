package com.runiverse.running_service.infrastructure.persistence.notification;

import com.runiverse.running_service.application.notification.port.out.SaveUserNotificationPort;
import com.runiverse.running_service.domain.notification.UserNotification;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserNotificationPersistenceAdapter implements SaveUserNotificationPort {

    private final EntityManager entityManager;

    @Override
    public void save(UserNotification notification) {
        // 종류는 payload가 정한다 — 컬럼과 jsonb가 어긋날 수 없다
        entityManager.persist(UserNotificationJpaEntity.create(
                notification.getUserId().value(),
                notification.type(),
                UserNotificationPayloadMapper.toJson(notification.getPayload())));
    }
}
