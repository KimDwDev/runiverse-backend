package com.runiverse.running_service.application.device.command.push;

import com.runiverse.running_service.application.device.port.out.PushMessage;

import java.util.UUID;

// 커밋된 뒤에 채팅 푸시를 보내라는 요청 — 다른 푸시와 발송 줄을 나누려고 PushRequestedEvent와 따로 둔다
public record RoomChatPushRequestedEvent(UUID userId, PushMessage message) {

}
