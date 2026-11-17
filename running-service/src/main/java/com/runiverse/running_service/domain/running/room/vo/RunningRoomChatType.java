package com.runiverse.running_service.domain.running.room.vo;

// 입장은 문구가 같아도 푸시 대상이 달라 처음과 다시를 나눈다
public enum RunningRoomChatType {
    MESSAGE,    // 참가자가 쓴 말
    JOINED,     // 처음 들어옴
    REJOINED,   // 나갔던 방에 다시 들어옴
    LEFT        // 나감
}
