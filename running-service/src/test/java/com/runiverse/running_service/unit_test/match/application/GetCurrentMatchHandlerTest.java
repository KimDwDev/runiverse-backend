package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.MatchProperties;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.query.currentmatch.GetCurrentMatchHandler;
import com.runiverse.running_service.application.match.query.currentmatch.GetCurrentMatchQuery;
import com.runiverse.running_service.application.match.query.currentmatch.GetCurrentMatchResult;
import com.runiverse.running_service.application.match.query.currentmatch.MatchState;
import com.runiverse.running_service.application.match.query.roominfo.RoomInfo;
import com.runiverse.running_service.application.match.query.roominfo.RoomInfoAssembler;
import com.runiverse.running_service.application.running.port.out.LoadActiveRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("현재 매칭 상태 조회 단위 테스트")
class GetCurrentMatchHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final Long PLAYER_ID = 7L;
    private static final Long ROOM_ID = 125L;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(15);
    private static final int AVG_PACE = 360;
    private static final int TARGET_DISTANCE = 5000;

    @Mock
    private LoadActiveRunningPlayerPort loadActiveRunningPlayerPort;

    @Mock
    private LoadMatchRoomPort loadMatchRoomPort;

    @Mock
    private LoadRunningRoomPort loadRunningRoomPort;

    @Mock
    private RoomInfoAssembler roomInfoAssembler;

    private GetCurrentMatchHandler getCurrentMatchHandler;

    @BeforeEach
    void setUp() {
        // 마감 오프셋은 운영값이라 목이 아니라 실제 값으로 고정한다 — 경계 판정이 이 값에 걸린다
        getCurrentMatchHandler = new GetCurrentMatchHandler(
                loadActiveRunningPlayerPort, loadMatchRoomPort, loadRunningRoomPort,
                roomInfoAssembler, new MatchProperties(CLOSE_OFFSET));
    }

    @Test
    @DisplayName("활성 신청이 없으면 NONE이고 방을 조회하지 않는다")
    void returnsNoneWhenNoActiveApplication() {
        // given
        given(loadActiveRunningPlayerPort.loadActive(new UserId(USER_ID)))
                .willReturn(Optional.empty());

        // when
        GetCurrentMatchResult result = getCurrentMatchHandler.handle(new GetCurrentMatchQuery(USER_ID));

        // then -> 대다수 사용자가 이 경로를 타므로 뒤 조회가 아예 나가지 않아야 한다
        assertThat(result.state()).isEqualTo(MatchState.NONE);
        assertThat(result.runningRoomId()).isNull();
        assertThat(result.room()).isNull();
        verifyNoInteractions(loadMatchRoomPort, loadRunningRoomPort, roomInfoAssembler);
    }

    @Test
    @DisplayName("러닝 중이면 매칭 단계가 아니라 NONE이다")
    void returnsNoneWhileRunning() {
        // given -> deleted_at은 비어 있지만 status가 RUNNING이라 활성 '신청'이 아니다(erd.md)
        given(loadActiveRunningPlayerPort.loadActive(new UserId(USER_ID)))
                .willReturn(Optional.of(player(RunningPlayerStatus.RUNNING)));

        // when
        GetCurrentMatchResult result = getCurrentMatchHandler.handle(new GetCurrentMatchQuery(USER_ID));

        // then
        assertThat(result.state()).isEqualTo(MatchState.NONE);
        verifyNoInteractions(loadMatchRoomPort, loadRunningRoomPort, roomInfoAssembler);
    }

    @Test
    @DisplayName("모집 중이고 마감 전이면 WAITING이다")
    void returnsWaitingBeforeClose() {
        // given -> 마감(start_at - 15분)이 아직 한참 남은 방
        givenActiveMatch(RunningRoomStatus.MATCHING, LocalDateTime.now().plusHours(2));

        // when
        GetCurrentMatchResult result = getCurrentMatchHandler.handle(new GetCurrentMatchQuery(USER_ID));

        // then
        assertThat(result.state()).isEqualTo(MatchState.WAITING);
        assertThat(result.runningRoomId()).isEqualTo(ROOM_ID);
        assertThat(result.room()).isNotNull();
    }

    @Test
    @DisplayName("마감이 지났는데 아직 MATCHING이면 MATCHED로 판정한다")
    void returnsMatchedWhenCloseTimePassedButSchedulerIsLate() {
        // given -> 시작까지 5분 남은 방이라 마감(start_at - 15분)은 이미 10분 전에 지났다.
        //          확정은 마감 시각에 일어난 사실이고 스케줄러는 반영이 늦을 뿐이다(api-spec 5-A)
        givenActiveMatch(RunningRoomStatus.MATCHING, LocalDateTime.now().plusMinutes(5));

        // when
        GetCurrentMatchResult result = getCurrentMatchHandler.handle(new GetCurrentMatchQuery(USER_ID));

        // then
        assertThat(result.state()).isEqualTo(MatchState.MATCHED);
    }

    @Test
    @DisplayName("방이 확정되면 마감 전이어도 MATCHED다")
    void returnsMatchedWhenRoomIsMatched() {
        // given
        givenActiveMatch(RunningRoomStatus.MATCHED, LocalDateTime.now().plusHours(2));

        // when
        GetCurrentMatchResult result = getCurrentMatchHandler.handle(new GetCurrentMatchQuery(USER_ID));

        // then
        assertThat(result.state()).isEqualTo(MatchState.MATCHED);
    }

    private void givenActiveMatch(RunningRoomStatus status, LocalDateTime startAt) {
        RunningRoom room = room(status, startAt);
        given(loadActiveRunningPlayerPort.loadActive(new UserId(USER_ID)))
                .willReturn(Optional.of(player(RunningPlayerStatus.JOINED)));
        given(loadMatchRoomPort.findAssignedRoom(new RunningPlayerId(PLAYER_ID)))
                .willReturn(Optional.of(new RunningRoomId(ROOM_ID)));
        given(loadRunningRoomPort.loadById(new RunningRoomId(ROOM_ID)))
                .willReturn(Optional.of(room));
        given(roomInfoAssembler.assemble(room)).willReturn(roomInfo(status, startAt));
    }

    private static RunningPlayer player(RunningPlayerStatus status) {
        return RunningPlayer.builder()
                .runningPlayerId(PLAYER_ID)
                .userId(USER_ID)
                .status(status)
                .avgPace(AVG_PACE)
                .targetDistance(TARGET_DISTANCE)
                .startAt(LocalDateTime.now().plusHours(2))
                .build();
    }

    // 모집 중·확정된 방은 아직 닫히지 않았으므로 closeAt은 비어 있어야 복원된다
    private static RunningRoom room(RunningRoomStatus status, LocalDateTime startAt) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.MATCH)
                .status(status)
                .startAt(startAt)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(new RunningPlayerId(PLAYER_ID), 0, true)))
                .build();
    }

    private static RoomInfo roomInfo(RunningRoomStatus status, LocalDateTime startAt) {
        return new RoomInfo(ROOM_ID, status, startAt, startAt.minus(CLOSE_OFFSET),
                TARGET_DISTANCE, AVG_PACE, List.of());
    }
}
