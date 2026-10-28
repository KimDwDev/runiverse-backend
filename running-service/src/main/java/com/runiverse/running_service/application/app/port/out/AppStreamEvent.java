package com.runiverse.running_service.application.app.port.out;

import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;

// 앱 스트림으로 나가는 이벤트 하나 — 종류에 따라 채워지는 값이 다르다.
// 알림 조립은 notification이 하고, 이 스트림은 받은 결과를 나르기만 한다
public record AppStreamEvent(AppStreamEventType type, GetUserNotificationsResult.Item notification) {

    // 새 알림 — 목록(2-4) 항목과 같은 조립기로 만든 값이라 모양이 같다
    public static AppStreamEvent notificationCreated(GetUserNotificationsResult.Item notification) {
        return new AppStreamEvent(AppStreamEventType.NOTIFICATION_CREATED, notification);
    }
}
