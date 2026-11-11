package com.runiverse.running_service.integration_test.match;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.broadcast.BroadMatchEventHandler;
import com.runiverse.running_service.application.match.command.broadcast.BroadcastMatchEventCommand;
import com.runiverse.running_service.application.match.command.stream.CloseMatchStreamCommand;
import com.runiverse.running_service.application.match.command.stream.CloseMatchStreamHandler;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamCommand;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamHandler;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomChat;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.infrastructure.redis.match.MatchRoomSubscriber;
import com.runiverse.running_service.infrastructure.sse.MatchRoomMemberRegistry;
import com.runiverse.running_service.infrastructure.sse.MatchRoomMembershipAdapter;
import com.runiverse.running_service.infrastructure.sse.MatchStreamRegistryAdapter;
import com.runiverse.running_service.integration_test.fake.RecordingMatchStreamConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// 한 인스턴스 안에서 사용자가 방을 갈아탈 때 이벤트가 어디로 가는지 본다(architecture.md 「다중 인스턴스」 1·3번).
// 연결표·명부·명부 어댑터·열기·닫기·방송 핸들러는 운영 구현을 그대로 엮고, 바깥 경계만 대신한다 —
// Redis 구독(MatchRoomSubscriber)은 가짜, 방 정보 조립은 고정값, SSE 연결은 받은 이벤트를 쌓는 기록용이다.
// 실제 SseEmitter는 complete() 뒤 종료 콜백을 따로 돌린다 — 여기서는 그 콜백(CloseMatchStreamHandler)을 직접 부른다
@DisplayName("매칭 스트림 방 갈아타기 통합 테스트")
class MatchStreamRoomSwitchIntegrationTest {

    private static final long OLD_ROOM = 125L;
    private static final long NEW_ROOM = 130L;
    private static final LocalDateTime START = LocalDateTime.now().plusDays(1).withHour(20).withMinute(0).withSecond(0).withNano(0);

    private final UUID chulsu = UuidCreator.getTimeOrderedEpoch();   // 방을 옮기는 사람
    private final UUID younghee = UuidCreator.getTimeOrderedEpoch(); // 옛 방에 남아 채팅을 보내는 사람
    private final UUID minji = UuidCreator.getTimeOrderedEpoch();    // 옛 방에 남아 이벤트를 받는 사람

    private MatchStreamRegistryAdapter streams;
    private MatchRoomMemberRegistry members;
    private RoomInfoAssembler roomInfoAssembler;
    private OpenMatchStreamHandler openHandler;
    private CloseMatchStreamHandler closeHandler;
    private BroadMatchEventHandler broadcastHandler;

    @BeforeEach
    void setUp() {
        streams = new MatchStreamRegistryAdapter();
        members = new MatchRoomMemberRegistry();
        MatchRoomMembershipAdapter membership = new MatchRoomMembershipAdapter(members, mock(MatchRoomSubscriber.class));
        roomInfoAssembler = mock(RoomInfoAssembler.class);
        openHandler = new OpenMatchStreamHandler(streams, membership, roomInfoAssembler);
        closeHandler = new CloseMatchStreamHandler(streams, membership);
        broadcastHandler = new BroadMatchEventHandler(members, streams);
    }

    private static RoomInfo room(long roomId) {
        return new RoomInfo(roomId, RunningRoomType.MATCH, RunningRoomStatus.MATCHING, START,
                START.minusMinutes(10), 5_000, 330, List.of(), List.of());
    }

    // 스트림을 연다 — 조립기는 그 시점에 배정된 방을 돌려준다(실제로는 DB의 배정)
    private RecordingMatchStreamConnection open(UUID userId, long roomId, String connectionId) {
        given(roomInfoAssembler.assembleFor(new UserId(userId))).willReturn(Optional.of(room(roomId)));
        RecordingMatchStreamConnection connection = new RecordingMatchStreamConnection(connectionId);
        openHandler.handle(new OpenMatchStreamCommand(userId, connection));
        return connection;
    }

    // SseEmitter의 종료 콜백 — 앱이 닫았든, 서버가 대체하며 닫았든 같은 경로다
    private void closed(UUID userId, RecordingMatchStreamConnection connection) {
        closeHandler.handle(new CloseMatchStreamCommand(userId, connection));
    }

    private void broadcast(MatchStreamEvent event) {
        broadcastHandler.handle(new BroadcastMatchEventCommand(event));
    }

    private static MatchStreamEvent chatFrom(UUID sender, long roomId) {
        return MatchStreamEvent.roomChatSent(new RoomChat(1L, roomId,
                new RoomChat.Sender(sender, "영희", null, false), "옛 방에서 보낸 말", false, null, START));
    }

    // 입장 스냅샷을 뺀 나머지 — 연결 직후 한 번은 항상 온다
    private static List<MatchStreamEvent> afterEntrance(RecordingMatchStreamConnection connection) {
        return connection.received().stream()
                .filter(event -> event.type() != MatchEventType.MATCH_ROOM_ENTRANCE)
                .toList();
    }

    @Test
    @DisplayName("옛 연결을 연 채 새 방으로 갈아타면 옛 방 채팅·인원 변동이 새 연결로 가지 않는다")
    void oldRoomEventsDoNotLeakIntoNewConnection() {
        // given - 셋이 옛 방에 있다
        RecordingMatchStreamConnection oldConnection = open(chulsu, OLD_ROOM, "chulsu-A");
        open(younghee, OLD_ROOM, "younghee");
        RecordingMatchStreamConnection minjiConnection = open(minji, OLD_ROOM, "minji");

        // when - 철수가 나가기 뒤 앱이 옛 연결을 닫기 전에 새 방 스트림을 연다.
        //        서버가 옛 연결을 대체하며 닫고, 종료 콜백은 그 뒤에 돈다 — 가드에 걸려 leave()를 건너뛴다
        RecordingMatchStreamConnection newConnection = open(chulsu, NEW_ROOM, "chulsu-B");
        closed(chulsu, oldConnection);

        // then - 옛 연결은 서버가 닫았다
        assertThat(oldConnection.isClosed()).isTrue();

        // when - 옛 방에서 영희가 말하고 인원이 바뀐다
        broadcast(chatFrom(younghee, OLD_ROOM));
        broadcast(MatchStreamEvent.updated(room(OLD_ROOM)));

        // then - 옛 방에 남은 민지는 둘 다 받는다(방송 자체는 정상)
        assertThat(afterEntrance(minjiConnection)).extracting(MatchStreamEvent::type)
                .containsExactly(MatchEventType.ROOM_CHAT_SENT, MatchEventType.MATCH_ROOM_UPDATED);
        // then - 새 방에 있는 철수의 새 연결에는 옛 방 이벤트가 하나도 오면 안 된다
        assertThat(afterEntrance(newConnection)).extracting(MatchStreamEvent::runningRoomId)
                .doesNotContain(OLD_ROOM);
        assertThat(members.usersIn(OLD_ROOM)).doesNotContain(new UserId(chulsu));
    }

    @Test
    @DisplayName("앱이 옛 연결을 먼저 닫고 새 방에 들어가면 옛 방 이벤트가 새 연결로 가지 않는다")
    void normalFlowDoesNotLeak() {
        // given
        RecordingMatchStreamConnection oldConnection = open(chulsu, OLD_ROOM, "chulsu-A");
        open(younghee, OLD_ROOM, "younghee");

        // when - 문서대로 나가기 204를 받고 스트림을 닫은 뒤 새 방 스트림을 연다
        closed(chulsu, oldConnection);
        RecordingMatchStreamConnection newConnection = open(chulsu, NEW_ROOM, "chulsu-B");
        broadcast(chatFrom(younghee, OLD_ROOM));

        // then - 이 순서에서는 가드가 통과해 leave()가 돈다
        assertThat(afterEntrance(newConnection)).isEmpty();
        assertThat(members.usersIn(OLD_ROOM)).doesNotContain(new UserId(chulsu));
    }

    @Test
    @DisplayName("같은 방으로 다시 연결하면 새 연결만 방 이벤트를 받고 옛 연결은 받지 않는다")
    void sameRoomReconnectMovesDeliveryToNewConnection() {
        // given
        RecordingMatchStreamConnection oldConnection = open(chulsu, OLD_ROOM, "chulsu-A");
        open(younghee, OLD_ROOM, "younghee");

        // when - 앱 재시작처럼 같은 방으로 새 연결이 붙고, 옛 연결의 종료 콜백은 그 뒤에 돈다
        RecordingMatchStreamConnection newConnection = open(chulsu, OLD_ROOM, "chulsu-B");
        closed(chulsu, oldConnection);
        broadcast(chatFrom(younghee, OLD_ROOM));

        // then - 늦게 돈 옛 연결 정리가 새 연결을 명부에서 빼지 않았다(가드가 지킨다)
        assertThat(afterEntrance(newConnection)).extracting(MatchStreamEvent::type)
                .containsExactly(MatchEventType.ROOM_CHAT_SENT);
        assertThat(afterEntrance(oldConnection)).isEmpty();
        assertThat(members.usersIn(OLD_ROOM)).contains(new UserId(chulsu));
    }

    @Test
    @DisplayName("갈아탄 뒤 새 연결까지 닫으면 옛 방·새 방 명부 어디에도 남지 않는다")
    void nothingRemainsAfterBothConnectionsClose() {
        // given
        RecordingMatchStreamConnection oldConnection = open(chulsu, OLD_ROOM, "chulsu-A");
        open(younghee, OLD_ROOM, "younghee");
        RecordingMatchStreamConnection newConnection = open(chulsu, NEW_ROOM, "chulsu-B");
        closed(chulsu, oldConnection);

        // when - 새 연결도 닫힌다(러닝 시작·앱 종료 등)
        closed(chulsu, newConnection);

        // then - 남으면 서버를 재시작할 때까지 옛 방 이벤트를 붙잡는다
        assertThat(members.usersIn(OLD_ROOM)).doesNotContain(new UserId(chulsu));
        assertThat(members.usersIn(NEW_ROOM)).doesNotContain(new UserId(chulsu));
    }
}
