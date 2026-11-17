package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomChatAssembler;
import com.runiverse.running_service.application.match.common.RoomChatPresenceWriter;
import com.runiverse.running_service.application.match.common.RoomChatPushRequester;
import com.runiverse.running_service.application.match.port.out.CreateRoomChatPort;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.match.port.out.SavedRoomChat;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.RunningRoom.SessionDraft;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomChatType;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("방 입장·퇴장 채팅 줄 단위 테스트")
class RoomChatPresenceWriterTest {

    private static final long ROOM_ID = 125L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 20, 19, 30);
    private static final LocalDateTime SAVED_AT = START.minusMinutes(20);
    private static final UUID HOST = UuidCreator.getTimeOrderedEpoch();
    private static final UUID GUEST = UuidCreator.getTimeOrderedEpoch();
    private static final UUID GONE = UuidCreator.getTimeOrderedEpoch();
    private static final RoomChat ASSEMBLED = new RoomChat(3022L, ROOM_ID, RunningRoomChatType.JOINED,
            null, new RoomChat.Person(GUEST, "영희", null, false), null, false, null, SAVED_AT);

    @Mock
    private CreateRoomChatPort createRoomChatPort;

    @Mock
    private RoomChatAssembler roomChatAssembler;

    @Mock
    private RoomChatPushRequester roomChatPushRequester;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RoomChatPresenceWriter writer;

    @BeforeEach
    void setUp() {
        writer = new RoomChatPresenceWriter(
                createRoomChatPort, roomChatAssembler, roomChatPushRequester, eventPublisher);
    }

    private void givenSaved() {
        given(createRoomChatPort.create(any())).willReturn(new SavedRoomChat(3022L, SAVED_AT));
        given(roomChatAssembler.assemble(any(RoomChatRow.class))).willReturn(ASSEMBLED);
    }

    // 손님의 세션은 이탈 횟수로 처음인지 다시인지 갈린다. 나간 사람은 배정이 풀려 있다
    private static RunningRoom room(int guestLeaveCount, int currentPlayerCount, boolean guestConnected) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.MATCHING)
                .startAt(START)
                .targetDistance(5_000)
                .avgPace(360)
                .currentPlayerCount(currentPlayerCount)
                .maxPlayerCount(4)
                .sessions(List.of(
                        new SessionDraft(new UserId(HOST), new RunningPlayerId(1L), 0, true),
                        new SessionDraft(new UserId(GUEST), new RunningPlayerId(2L), guestLeaveCount, guestConnected),
                        new SessionDraft(new UserId(GONE), new RunningPlayerId(3L), 1, false)))
                .build();
    }

    @Test
    @DisplayName("처음 들어오면 입장 줄을 남기고 실시간으로 알린 뒤 배정된 사람에게 푸시를 요청한다")
    void firstJoin() {
        // given
        givenSaved();
        RunningRoom room = room(0, 2, true);

        // when
        writer.joined(room, new UserId(GUEST));

        // then -> 나간 사람(GONE)은 받는 사람에 없다. 당사자는 요청기가 뺀다
        ArgumentCaptor<RunningRoomChat> saved = ArgumentCaptor.forClass(RunningRoomChat.class);
        verify(createRoomChatPort).create(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(RunningRoomChatType.JOINED);
        assertThat(saved.getValue().getUserId()).isEqualTo(new UserId(GUEST));
        assertThat(saved.getValue().getRunningRoomId()).isEqualTo(new RunningRoomId(ROOM_ID));
        verify(roomChatAssembler).assemble(new RoomChatRow(3022L, ROOM_ID, GUEST, RunningRoomChatType.JOINED,
                null, null, null, SAVED_AT));
        ArgumentCaptor<MatchRoomChangedEvent> published = ArgumentCaptor.forClass(MatchRoomChangedEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().event().type()).isEqualTo(MatchEventType.ROOM_CHAT_SENT);
        assertThat(published.getValue().event().chat()).isEqualTo(ASSEMBLED);
        verify(roomChatPushRequester).request(ASSEMBLED, START, 5_000,
                List.of(new UserId(HOST), new UserId(GUEST)));
    }

    @Test
    @DisplayName("나갔던 방에 다시 들어오면 다시 들어온 줄을 남기고 실시간으로만 알린다")
    void rejoin() {
        // given
        givenSaved();

        // when
        writer.joined(room(1, 2, true), new UserId(GUEST));

        // then -> 대기 취소·재신청을 되풀이해도 방 사람들 폰이 울리지 않는다
        ArgumentCaptor<RunningRoomChat> saved = ArgumentCaptor.forClass(RunningRoomChat.class);
        verify(createRoomChatPort).create(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(RunningRoomChatType.REJOINED);
        verify(eventPublisher).publishEvent(any(MatchRoomChangedEvent.class));
        verifyNoInteractions(roomChatPushRequester);
    }

    @Test
    @DisplayName("나가면 퇴장 줄을 남기고 남은 사람에게 실시간으로만 알린다")
    void leave() {
        // given
        givenSaved();

        // when
        writer.left(room(1, 1, false), new UserId(GUEST));

        // then
        ArgumentCaptor<RunningRoomChat> saved = ArgumentCaptor.forClass(RunningRoomChat.class);
        verify(createRoomChatPort).create(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(RunningRoomChatType.LEFT);
        assertThat(saved.getValue().getUserId()).isEqualTo(new UserId(GUEST));
        verify(eventPublisher).publishEvent(any(MatchRoomChangedEvent.class));
        verifyNoInteractions(roomChatPushRequester);
    }

    @Test
    @DisplayName("마지막 사람이 나가도 줄은 남기지만 받을 사람이 없어 알리지 않는다")
    void lastLeave() {
        // given
        givenSaved();

        // when
        writer.left(room(1, 0, false), new UserId(GUEST));

        // then
        verify(createRoomChatPort).create(any());
        verifyNoInteractions(eventPublisher, roomChatPushRequester);
    }
}
