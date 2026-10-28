package com.runiverse.running_service.application.user.port.out;

// 유저 스트림이 보내는 이벤트 — 이름이 곧 SSE event 이름이고, 클라는 이 값으로 할 일을 가른다.
// USER_STATUS는 연결 직후 한 번 보내는 스냅샷이다 — 재연결이 곧 상태 재수신이라 놓친 이벤트를 되짚지 않는다
public enum UserStreamEventType {
    USER_STATUS
}
