package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamCommand;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamHandler;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchRoomMembershipPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 스트림 연결 단위 테스트")
class OpenMatchStreamHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long ROOM_ID = 125L;
    private static final RoomInfo ROOM_INFO = new RoomInfo(
            ROOM_ID, RunningRoomStatus.MATCHING, LocalDateTime.now().plusHours(2),
            LocalDateTime.now().plusHours(1), 5_000, 360, List.of());

    @Mock
    private MatchStreamPort matchStreamPort;

    @Mock
    private MatchStreamConnection newConnection;

    @Mock
    private MatchStreamConnection supersededConnection;

    @Mock
    private MatchRoomMembershipPort matchRoomMembershipPort;

    @Mock
    private RoomInfoAssembler roomInfoAssembler;

    @InjectMocks
    private OpenMatchStreamHandler openMatchStreamHandler;

    @Test
    @DisplayName("밀려난 이전 연결은 닫는다")
    void closesSupersededConnection() {
        // given -> 같은 유저가 옛 연결을 남긴 채 다시 붙은 상황
        given(matchStreamPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.of(supersededConnection));
        given(roomInfoAssembler.assembleFor(new UserId(USER_ID))).willReturn(Optional.empty());

        // when
        openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection));

        // then
        verify(supersededConnection).closeSuperseded();
    }

    @Test
    @DisplayName("밀어낼 연결이 없으면 아무 연결도 닫지 않는다")
    void closesNothingWhenNoSupersededConnection() {
        // given
        given(matchStreamPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.empty());
        given(roomInfoAssembler.assembleFor(new UserId(USER_ID))).willReturn(Optional.empty());

        // when
        openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection));

        // then
        verifyNoInteractions(supersededConnection);
    }

    @Test
    @DisplayName("활성 신청이 있으면 방 채널을 구독하고 스냅샷을 보낸다")
    void subscribesAndSendsSnapshot() {
        // given -> 재연결이 곧 스냅샷 재수신이다(feature-spec)
        given(matchStreamPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.empty());
        given(roomInfoAssembler.assembleFor(new UserId(USER_ID)))
                .willReturn(Optional.of(ROOM_INFO));

        // when
        openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection));

        // then -> 구독이 먼저다. 스냅샷을 보내는 사이에 온 갱신을 놓치지 않는다
        verify(matchRoomMembershipPort).join(new UserId(USER_ID), ROOM_ID);
        verify(newConnection).send(new MatchStreamEvent(MatchEventType.MATCH_ROOM_UPDATED, ROOM_INFO));
    }

    @Test
    @DisplayName("활성 신청이 없으면 구독도 발신도 하지 않는다")
    void skipsWhenNoActiveApplication() {
        // given -> 스펙상 클라는 그때 연결하지 않지만 서버가 막지는 않는다
        given(matchStreamPort.register(new UserId(USER_ID), newConnection))
                .willReturn(Optional.empty());
        given(roomInfoAssembler.assembleFor(new UserId(USER_ID))).willReturn(Optional.empty());

        // when
        openMatchStreamHandler.handle(new OpenMatchStreamCommand(USER_ID, newConnection));

        // then
        verifyNoInteractions(matchRoomMembershipPort);
        verify(newConnection, org.mockito.Mockito.never()).send(any(MatchStreamEvent.class));
    }
}
