package com.runiverse.running_service.application.match.common;

import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 채팅 한 줄을 방의 다른 사람 폰에 알린다 — 러닝이 시작되면 채팅이 닫혀 시작 시각에 만료된다.
// 보내는 순간의 내용·받는 사람 그대로 간다(대기 줄에 있는 사이 지우거나 나가도 다시 보지 않는다).
// 커밋 뒤에 보내므로 줄을 저장한 트랜잭션 안에서 부른다
@Component
@RequiredArgsConstructor
public class RoomChatPushRequester {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ApplicationEventPublisher eventPublisher;

    // 받는 사람은 지금 방에 배정된 사람 중 그 줄의 당사자를 뺀 전원이다
    public void request(RoomChat chat, LocalDateTime startAt, Integer targetDistanceMeters, List<UserId> roomMembers) {
        UserId person = new UserId(chat.person().userId());
        List<UserId> recipients = roomMembers.stream()
                .filter(member -> !member.equals(person))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        PushMessage message = new PushMessage(PushType.ROOM_CHAT_SENT,
                titleOf(startAt, targetDistanceMeters), bodyOf(chat), dataOf(chat), startAt);
        recipients.forEach(userId ->
                eventPublisher.publishEvent(new RoomChatPushRequestedEvent(userId.value(), message)));
    }

    // 앱 채팅 제목 윗줄과 같다 — "19:30 - 5 km 러닝", 거리를 모르면 "19:30 러닝"
    private static String titleOf(LocalDateTime startAt, Integer targetDistanceMeters) {
        String time = startAt.format(TIME_FORMAT);
        if (targetDistanceMeters == null) {
            return "%s 러닝".formatted(time);
        }
        String km = BigDecimal.valueOf(targetDistanceMeters, 3).stripTrailingZeros().toPlainString();
        return "%s - %s km 러닝".formatted(time, km);
    }

    // 보낸 사람 이름은 알림의 이름 칸에 따로 나와 말 본문에 붙이지 않는다
    private static String bodyOf(RoomChat chat) {
        return switch (chat.type()) {
            case MESSAGE -> chat.content();
            case JOINED -> "%s님이 들어왔어요".formatted(chat.actor().nickname());
            case REJOINED, LEFT -> throw new IllegalArgumentException("푸시하지 않는 줄이다 — type=" + chat.type());
        };
    }

    // 사진 주소는 정해진 시간이 지나면 만료된다 — 그 뒤에 받은 앱은 사진 없이 그린다
    private static Map<String, String> dataOf(RoomChat chat) {
        RoomChat.Person person = chat.person();
        Map<String, String> data = new HashMap<>();
        data.put("runningRoomId", String.valueOf(chat.runningRoomId()));
        data.put("chatId", String.valueOf(chat.chatId()));
        data.put("chatType", chat.type().name());
        data.put("actorId", person.userId().toString());
        data.put("actorNickname", person.nickname());
        if (person.profileImageUrl() != null) {
            data.put("actorProfileImageUrl", person.profileImageUrl());
        }
        return data;
    }
}
