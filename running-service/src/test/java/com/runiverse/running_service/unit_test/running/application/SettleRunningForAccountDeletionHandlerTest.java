package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.stream.MatchStreamCloseRequestedEvent;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.LockMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.running.command.accountdeletion.SettleRunningForAccountDeletionCommand;
import com.runiverse.running_service.application.running.command.accountdeletion.SettleRunningForAccountDeletionHandler;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.application.running.port.out.DeleteRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원탈퇴 러닝 정리 단위 테스트")
class SettleRunningForAccountDeletionHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long PLAYER_ID = 7L;
    private static final long ROOM_ID = 125L;
    private static final int AVG_PACE = 360;
    private static final int TARGET_DISTANCE = 5_000;
    // 조립 결과는 이 테스트의 주제가 아니다 — 발행 여부만 본다
    private static final RoomInfo ROOM_INFO = new RoomInfo(
            ROOM_ID, RunningRoomStatus.MATCHING, LocalDateTime.now().plusHours(2),
            LocalDateTime.now().plusHours(1), TARGET_DISTANCE, AVG_PACE, List.of());

    @Mock
    private LockMatchApplicationPort lockMatchApplicationPort;
    @Mock
    private LoadMatchRoomPort loadMatchRoomPort;
    @Mock
    private LockRunningRoomPort lockRunningRoomPort;
    @Mock
    private UpdateMatchRoomPort updateMatchRoomPort;
    @Mock
    private DeleteRunningPlayerPort deleteRunningPlayerPort;
    @Mock
    private FinishRunningUsecase finishRunningUsecase;
    @Mock
    private RoomInfoAssembler roomInfoAssembler;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SettleRunningForAccountDeletionHandler handler;

    @Test
    @DisplayName("활성 신청이 없어도 매칭 스트림 종료는 요청한다")
    void requestsStreamCloseEvenWithoutActiveApplication() {
        // given -> 탈퇴자 대부분이 이 경우다
        given(lockMatchApplicationPort.lockActive(new UserId(USER_ID)))
                .willReturn(Optional.empty());

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then -> 연결은 커밋 뒤에 닫는다. 여기서 닫으면 롤백돼도 되살릴 수 없다
        verify(eventPublisher).publishEvent(new MatchStreamCloseRequestedEvent(new UserId(USER_ID)));
        verifyNoInteractions(loadMatchRoomPort, lockRunningRoomPort,
                deleteRunningPlayerPort, finishRunningUsecase);
    }

    @Test
    @DisplayName("시작 전 방이면 신청과 세션을 지운다")
    void deletesApplicationWhenRoomNotStarted() {
        // given -> 모집 중인 방. 이탈 이력이 아니라 신청 자체가 없던 일이 된다
        givenActiveApplication(room(RunningRoomStatus.MATCHING, 2));
        given(roomInfoAssembler.assemble(any(RunningRoom.class))).willReturn(ROOM_INFO);

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then
        ArgumentCaptor<RunningPlayer> captor = ArgumentCaptor.forClass(RunningPlayer.class);
        verify(deleteRunningPlayerPort).delete(captor.capture());
        assertThat(captor.getValue().getRunningPlayerId())
                .contains(new RunningPlayerId(PLAYER_ID));
        verify(finishRunningUsecase, never()).handle(any());
    }

    @Test
    @DisplayName("확정된 방에서 나가면 인원이 줄고 방은 유지된다")
    void leavesMatchedRoomAndKeepsIt() {
        // given -> 남은 사람이 있으면 방을 닫지 않는다
        givenActiveApplication(room(RunningRoomStatus.MATCHED, 2));
        given(roomInfoAssembler.assemble(any(RunningRoom.class))).willReturn(ROOM_INFO);

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then
        RunningRoom updated = updatedRoom();
        assertThat(updated.getPlayerCount().current()).isOne();
        assertThat(updated.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
    }

    @Test
    @DisplayName("혼자였던 방은 인원이 0이 되어 취소된다")
    void cancelsRoomWhenLastPlayerLeaves() {
        // given
        givenActiveApplication(room(RunningRoomStatus.MATCHING, 1));

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then
        RunningRoom updated = updatedRoom();
        assertThat(updated.getPlayerCount().current()).isZero();
        assertThat(updated.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
    }

    @Test
    @DisplayName("인원이 0이면 남은 참가자가 없어 이벤트를 발행하지 않는다")
    void skipsEventWhenRoomBecomesEmpty() {
        // given
        givenActiveApplication(room(RunningRoomStatus.MATCHING, 1));

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then -> 스트림 종료 요청 하나만 나간다
        verify(eventPublisher).publishEvent(new MatchStreamCloseRequestedEvent(new UserId(USER_ID)));
        verify(eventPublisher, never()).publishEvent(any(MatchRoomChangedEvent.class));
    }

    @Test
    @DisplayName("남은 참가자에게 방 갱신을 발행한다")
    void publishesRoomUpdateToRemainingPlayers() {
        // given
        givenActiveApplication(room(RunningRoomStatus.MATCHING, 2));
        given(roomInfoAssembler.assemble(any(RunningRoom.class))).willReturn(ROOM_INFO);

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then
        verify(eventPublisher).publishEvent(new MatchRoomChangedEvent(
                new MatchStreamEvent(MatchEventType.MATCH_ROOM_UPDATED, ROOM_INFO)));
    }

    @Test
    @DisplayName("시작한 방이면 종료 경로로 보내고 신청을 남긴다")
    void finishesRunningWhenRoomStarted() {
        // given -> 기록이 없는 참가자도 과거 결과에 남아야 한다
        givenActiveApplication(room(RunningRoomStatus.STARTED, 2));

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then
        verify(finishRunningUsecase).handle(new FinishRunningCommand(ROOM_ID, USER_ID, true));
        verify(deleteRunningPlayerPort, never()).delete(any());
        verify(updateMatchRoomPort, never()).update(any());
    }

    @Test
    @DisplayName("방이 시작됐으면 참가자 상태가 JOINED여도 종료 경로로 간다")
    void finishesRunningEvenWhenPlayerStillJoined() {
        // given -> 시작 시각에 앱을 켜지 않은 참가자는 status가 JOINED로 남는다
        givenActiveApplication(room(RunningRoomStatus.STARTED, 2), RunningPlayerStatus.JOINED);

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then -> 참가자 status로 갈랐다면 신청을 지웠을 것이다
        verify(finishRunningUsecase).handle(new FinishRunningCommand(ROOM_ID, USER_ID, true));
        verify(deleteRunningPlayerPort, never()).delete(any());
    }

    private void givenActiveApplication(RunningRoom room) {
        givenActiveApplication(room, RunningPlayerStatus.JOINED);
    }

    private void givenActiveApplication(RunningRoom room, RunningPlayerStatus status) {
        given(lockMatchApplicationPort.lockActive(new UserId(USER_ID)))
                .willReturn(Optional.of(player(status)));
        given(loadMatchRoomPort.findAssignedRoom(new UserId(USER_ID)))
                .willReturn(Optional.of(new RunningRoomId(ROOM_ID)));
        given(lockRunningRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.of(room));
    }

    private RunningRoom updatedRoom() {
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(updateMatchRoomPort).update(captor.capture());
        return captor.getValue();
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

    // 나 말고 나머지 인원은 다른 유저의 세션으로 채운다 — 세션 키가 유저다
    private static RunningRoom room(RunningRoomStatus status, int currentPlayerCount) {
        List<SessionDraft> sessions = new ArrayList<>();
        sessions.add(new SessionDraft(
                new UserId(USER_ID), new RunningPlayerId(PLAYER_ID), 0, true));
        for (int i = 1; i < currentPlayerCount; i++) {
            sessions.add(new SessionDraft(
                    new UserId(UuidCreator.getTimeOrderedEpoch()),
                    new RunningPlayerId(PLAYER_ID + i), 0, true));
        }
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.MATCH)
                .status(status)
                .startAt(LocalDateTime.now().plus(Duration.ofHours(2)))
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(currentPlayerCount)
                .maxPlayerCount(4)
                .sessions(sessions)
                .build();
    }
}
