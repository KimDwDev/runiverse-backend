package com.runiverse.running_service.application.app.port.out;

// 앱 스트림이 보내는 이벤트 — 이름이 곧 SSE event 이름이고, 클라는 이 값으로 할 일을 가른다.
// 연결 직후 보내는 스냅샷은 없다 — 끊긴 사이의 일은 앱이 REST로 다시 받는다
public enum AppStreamEventType {
    // 새 알림 하나 — 2-4 목록 항목과 같은 모양이다. 클라는 목록 맨 위에 붙이고 배지를 하나 올린다
    NOTIFICATION_CREATED
}
