package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatType;

import java.time.LocalDateTime;
import java.util.UUID;

// 채팅 한 건을 화면에 그릴 모양으로 조립한 값 — 보내기 응답과 ROOM_CHAT_* 이벤트 data가 같이 쓴다.
// 도메인 RunningRoomChat에는 보낸 사람 프로필과 보낸 시각이 없어 여기서 붙인다.
// 와이어 모양(chatId 등 이름)은 presentation 응답이 갖고, 여기는 값만 나른다
public record RoomChat(
        Long chatId,
        Long runningRoomId,
        RunningRoomChatType type,
        // 말을 보낸 사람 — 안내 줄이면 null
        Person sender,
        // 들어오거나 나간 사람 — 말이면 null
        Person actor,
        // 지운 메시지·안내 줄이면 null — 내용은 행에 남지만 API로는 다시 내려가지 않는다
        String content,
        boolean isDeleted,
        // 고친 적 없으면 null
        LocalDateTime editedAt,
        LocalDateTime createdAt
) {

    // 이 줄의 당사자 — 실시간 이벤트와 푸시를 받지 않는다
    public Person person() {
        return sender != null ? sender : actor;
    }

    public record Person(
            UUID userId,
            String nickname,
            String profileImageUrl,
            // 탈퇴했다 — 채팅의 isDeleted(메시지를 지움)와 다르다
            boolean isDeleted
    ) {

    }
}
