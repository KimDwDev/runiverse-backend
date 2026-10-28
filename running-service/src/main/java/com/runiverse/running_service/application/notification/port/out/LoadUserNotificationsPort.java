package com.runiverse.running_service.application.notification.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.LocalDateTime;

public interface LoadUserNotificationsPort {

    // 받는 사람의 알림을 최신순(ID 역순)으로 limit개까지 읽는다.
    // beforeId가 있으면 그보다 오래된 것부터, since보다 먼저 쌓인 것(보관 기간 지남)은 빼고 읽는다.
    // payload를 되살리지 못한 행은 로그를 남기고 빼되, 다음 페이지 기준은 뺀 행까지 포함해 정한다
    UserNotificationPage loadPage(UserId userId, Long beforeId, LocalDateTime since, int limit);
}
