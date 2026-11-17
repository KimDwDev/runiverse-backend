package com.runiverse.running_service.application.match.common;

import com.runiverse.running_service.application.match.port.out.CreateRoomChatPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.match.port.out.SavedRoomChat;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatType;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

// 방에 들어오고 나간 것을 채팅 안내 줄로 남긴다 — 참가 상태를 바꾼 트랜잭션 안에서, 방을 바꾼 뒤에 부른다.
// 줄은 그 당사자를 뺀 방 사람에게 실시간으로 가고, 푸시는 처음 들어올 때만 간다
@Component
@RequiredArgsConstructor
public class RoomChatPresenceWriter {

    private final CreateRoomChatPort createRoomChatPort;
    private final RoomChatAssembler roomChatAssembler;
    private final RoomChatPushRequester roomChatPushRequester;
    private final ApplicationEventPublisher eventPublisher;

    // 나갔던 방에 다시 배정되면 세션이 되살아나 이탈 횟수가 남아 있다 — 푸시하지 않는다.
    // 대기 취소는 곧바로 다시 신청할 수 있어, 푸시하면 취소·재신청마다 방 사람들에게 간다
    public void joined(RunningRoom room, UserId userId) {
        RunningRoomId roomId = room.getRunningRoomId().orElseThrow();
        RunningRoomChat line = room.hasLeftBefore(userId)
                ? RunningRoomChat.rejoined(roomId, userId)
                : RunningRoomChat.joined(roomId, userId);
        RoomChat chat = write(line);
        eventPublisher.publishEvent(new MatchRoomChangedEvent(MatchStreamEvent.roomChatSent(chat)));
        if (line.getType() == RunningRoomChatType.JOINED) {
            roomChatPushRequester.request(chat, room.getStartAt(),
                    room.getTargetDistance().map(Distance::meters).orElse(null), membersOf(room));
        }
    }

    // 시작 전에 나갈 때만 부른다
    public void left(RunningRoom room, UserId userId) {
        RoomChat chat = write(RunningRoomChat.left(room.getRunningRoomId().orElseThrow(), userId));
        // 인원이 0이면 받을 사람이 없다
        if (room.getPlayerCount().current() > 0) {
            eventPublisher.publishEvent(new MatchRoomChangedEvent(MatchStreamEvent.roomChatSent(chat)));
        }
    }

    // 방금 저장한 행과 같은 모양으로 조립한다 — 목록에서 다시 읽어도 같은 값이 나온다
    private RoomChat write(RunningRoomChat line) {
        SavedRoomChat saved = createRoomChatPort.create(line);
        return roomChatAssembler.assemble(new RoomChatRow(
                saved.chatId(),
                line.getRunningRoomId().value(),
                line.getUserId().value(),
                line.getType(),
                null,
                null,
                null,
                saved.createdAt()));
    }

    private static List<UserId> membersOf(RunningRoom room) {
        return room.getSessions().stream()
                .filter(RoomSession::isConnected)
                .map(RoomSession::getUserId)
                .toList();
    }
}
