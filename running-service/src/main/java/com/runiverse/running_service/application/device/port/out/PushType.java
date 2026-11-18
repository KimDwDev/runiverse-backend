package com.runiverse.running_service.application.device.port.out;

// 앱이 data의 type으로 눌렀을 때 갈 화면을 고른다 — 값 이름이 곧 앱과의 계약이다
public enum PushType {
    FRIEND_REQUESTED,
    FRIEND_ACCEPTED,
    RUNNING_INVITED,
    MATCH_CONFIRMED,
    RUNNING_REMINDER,
    ROOM_CHAT_SENT,
    // 운영이 고른 사람에게 보내는 공지 — 갈 화면이 없어 앱은 열기만 한다
    NOTICE
}
