package com.runiverse.running_service.application.notification.command.send;

import com.runiverse.running_service.application.common.port.out.SendUserNotificationPort;
import com.runiverse.running_service.application.notification.port.out.SaveUserNotificationPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.UserNotification;
import com.runiverse.running_service.domain.notification.vo.UserNotificationPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 알림을 부르는 동작과 같은 트랜잭션에 쌓는다 — 동작만 남거나 알림만 남지 않는다.
// 스트림으로 밀어 주는 일은 커밋 뒤로 미룬다(알림 푸시 단계에서 붙인다)
@Service
@RequiredArgsConstructor
@Transactional
public class SendUserNotificationHandler implements SendUserNotificationPort {

    private final SaveUserNotificationPort saveUserNotificationPort;

    @Override
    public void send(UserId userId, UserNotificationPayload payload) {
        saveUserNotificationPort.save(UserNotification.of(userId, payload));
    }
}
