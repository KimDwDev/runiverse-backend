package com.runiverse.running_service.application.notification.port.out;

import com.runiverse.running_service.domain.notification.UserNotification;

public interface SaveUserNotificationPort {

    // 새 알림을 쌓는다 — 알림을 만드는 동작과 같은 트랜잭션에서 부른다
    void save(UserNotification notification);
}
