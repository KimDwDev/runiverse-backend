package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishRunningRoomCommand;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishProperties;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishRunnerDispatcher;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishRunningRoomHandler;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishSteps;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.application.running.exception.RunningFinishInProgressException;
import com.runiverse.running_service.application.running.port.out.ExistsRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.LockRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.StartMatchCooldownPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.RunningRoom.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import com.runiverse.running_service.application.running.common.RunningComboArchiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 강제 종료 단위 테스트")
class ForceFinishRunningRoomHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final int AVG_PACE = 360;
    private static final int TARGET_DISTANCE = 5_000;
    private static final Duration MATCH_COOLDOWN = Duration.ofMinutes(20);
    // 오프셋(6시간)은 다시 걸기를 멈출 시각을 낸다 — 시작 + 6시간 + 보관 기간
    private static final MatchProperties PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofSeconds(10), Duration.ofHours(6),
            10, MATCH_COOLDOWN,
            LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));

    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(1);
    private static final ForceFinishProperties FORCE_FINISH_PROPERTIES =
            new ForceFinishProperties(RETRY_INTERVAL, Duration.ofHours(7));

    private static final UserId RUNNER = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId NO_SHOW = new UserId(UuidCreator.getTimeOrderedEpoch());

    @Mock
    private LockRunningPlayerPort lockRunningPlayerPort;

    @Mock
    private LockRunningRoomPort lockRunningRoomPort;

    @Mock
    private LoadRunningRoomPort loadRunningRoomPort;

    @Mock
    private LoadRoomPlayerPort loadRoomPlayerPort;

    @Mock
    private UpdateRunningRoomPort updateRunningRoomPort;

    @Mock
    private UpdateRunningPlayerPort updateRunningPlayerPort;

    @Mock
    private StartMatchCooldownPort startMatchCooldownPort;

    @Mock
    private ExistsRunningRecordPort existsRunningRecordPort;

    @Mock
    private FinishRunningUsecase finishRunningUsecase;

    @Mock
    private RunningComboArchiver runningComboArchiver;

    private ForceFinishRunningRoomHandler forceFinishRunningRoomHandler;

    // 잠금 안 단계와 워커는 실물을 쓴다 — 무엇을 잠그고 언제 넘기는지가 이 테스트의 주제다.
    // 워커는 그 자리에서 돈다(스레드를 띄우면 단언 시점에 끝났는지 알 수 없다)
    @BeforeEach
    void setUp() {
        ForceFinishSteps steps = new ForceFinishSteps(lockRunningPlayerPort, lockRunningRoomPort,
                loadRoomPlayerPort, updateRunningRoomPort, updateRunningPlayerPort, startMatchCooldownPort,
                existsRunningRecordPort, PROPERTIES, runningComboArchiver);
        forceFinishRunningRoomHandler = new ForceFinishRunningRoomHandler(
                loadRunningRoomPort, loadRoomPlayerPort, steps,
                new ForceFinishRunnerDispatcher(Runnable::run, finishRunningUsecase),
                PROPERTIES, FORCE_FINISH_PROPERTIES);
    }

    @Test
    @DisplayName("뛰던 참가자는 평소 러닝 종료 경로로 넘긴다")
    void delegatesRunnerToFinishRunning() {
        // given -> 트랙 분석·기록 생성·거리 비율 판정을 여기서 다시 만들지 않는다
        givenRoom(() -> room(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));
        givenPlayer(RUNNER, RunningPlayerStatus.RUNNING);
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 강제 종료라는 사실만 넘기고 최종 상태는 그쪽이 정한다
        verify(finishRunningUsecase).handle(
                new FinishRunningCommand(ROOM_ID, RUNNER.value(), true));
    }

    @Test
    @DisplayName("참가자를 방보다 먼저 잠근다")
    void locksPlayersBeforeRoom() {
        // given -> 시작·취소·탈퇴 정산이 모두 참가자 → 방 순이라, 반대로 잡으면 교착에 빠진다
        givenRoom(() -> room(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));
        givenPlayer(RUNNER, RunningPlayerStatus.RUNNING);
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then
        InOrder inOrder = inOrder(lockRunningPlayerPort, lockRunningRoomPort);
        inOrder.verify(lockRunningPlayerPort).lockActiveInRoom(new RunningRoomId(ROOM_ID));
        inOrder.verify(lockRunningRoomPort).lockById(new RunningRoomId(ROOM_ID));
    }

    @Test
    @DisplayName("한 번도 붙지 않은 참가자는 확정 후 이탈로 닫는다")
    void closesNoShowAsMatchedLeft() {
        // given -> 2인 확정 방이라 안 나타난 것이 곧 상대를 곤란하게 만든 것이다
        givenRoom(() -> room(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));
        givenPlayer(RUNNER, RunningPlayerStatus.RUNNING);
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> RUNNING을 거치지 않았으므로 조기 종료가 아니라 확정 후 이탈이다
        assertThat(updatedPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        // 상태가 MATCHED_LEFT_*라 쿨다운도 조기 종료가 아니라 이탈 쪽 값을 쓴다
        verify(startMatchCooldownPort).start(NO_SHOW, MATCH_COOLDOWN);
    }

    @Test
    @DisplayName("1인 확정 방의 미출석은 제재하지 않는다")
    void doesNotPenalizeNoShowInSinglePlayerRoom() {
        // given -> 안 나타나도 곤란해지는 상대가 없다. 시작 전 이탈 면제와 같은 기준이다
        givenRoom(() -> room(RunningRoomStatus.STARTED, NO_SHOW));
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then
        assertThat(updatedPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        verifyNoInteractions(startMatchCooldownPort);
    }

    @Test
    @DisplayName("판정 기준은 순회 전에 굳혀 전원에게 같게 적용한다")
    void freezesPenaltyBaseBeforeLoop() {
        // given -> 2인 방에서 둘 다 안 나타났다. 처리하면서 인원을 세면
        //          뒤에 처리된 사람만 1인 방으로 보여 면제받는다
        givenRoom(() -> room(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));
        givenPlayer(RUNNER, RunningPlayerStatus.JOINED);
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 둘 다 제재 대상이다
        assertThat(updatedPlayers())
                .extracting(RunningPlayer::getStatus)
                .containsOnly(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        verify(startMatchCooldownPort).start(RUNNER, MATCH_COOLDOWN);
        verify(startMatchCooldownPort).start(NO_SHOW, MATCH_COOLDOWN);
    }

    @Test
    @DisplayName("초대 방의 미출석은 제재하지 않는다")
    void doesNotPenalizeNoShowInInviteRoom() {
        // given -> 매칭 방이었으면 둘 다 제재 대상이다(2인 확정 방에서 아무도 안 나타났다)
        givenRoom(() -> inviteRoom(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));
        givenPlayer(RUNNER, RunningPlayerStatus.JOINED);
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then
        assertThat(updatedPlayers())
                .extracting(RunningPlayer::getStatus)
                .containsOnly(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        verifyNoInteractions(startMatchCooldownPort);
    }

    @Test
    @DisplayName("아무도 뛰지 않은 방은 남길 기록이 없어 취소로 닫고 완료로 답한다")
    void cancelsRoomNobodyRanIn() {
        // given -> 전원이 채널에 붙지 않았다. 방을 닫아 줄 러닝 종료가 하나도 없다
        givenRoom(() -> room(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));
        givenPlayer(RUNNER, RunningPlayerStatus.JOINED);
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);

        // when
        Optional<LocalDateTime> retryAt =
                forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then
        assertThat(lastUpdatedRoom().getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
        assertThat(lastUpdatedRoom().getCloseAt()).isPresent();
        verifyNoInteractions(finishRunningUsecase);
        // 남길 기록이 없어 콤보 상태만 지운다
        verify(runningComboArchiver).discardAfterCommit(ROOM_ID);
        // 남은 러너가 없다 — 예약을 완료로 적는다
        assertThat(retryAt).isEmpty();
    }

    @Test
    @DisplayName("러닝 종료가 이미 닫고 자리도 비운 방은 잠그지 않고 완료로 답한다")
    void finishesWithoutLockWhenAllDone() {
        // given -> 마지막 참가자가 끝나는 순간 러닝 종료 안에서 방이 FINISHED가 되고 자리도 비었다.
        //          흔한 마지막 알람이다 — 다 끝난 것을 확인만 한다
        givenSnapshot(finishedRoomWithoutSessions());

        // when
        Optional<LocalDateTime> retryAt =
                forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 다시 닫으면 그 전이를 덮어써 기록 있는 방이 취소로 남는다
        assertThat(retryAt).isEmpty();
        verifyNoInteractions(lockRunningPlayerPort, lockRunningRoomPort, updateRunningRoomPort,
                existsRunningRecordPort, runningComboArchiver, finishRunningUsecase);
    }

    @Test
    @DisplayName("러닝 종료가 방을 닫지 못했으면 기록 유무로 닫는다")
    void closesRoomLeftOpenByRecordExistence() {
        // given -> 뛴 사람은 확정됐는데 방이 열린 채로 남았다(확정 뒤 방 닫기가 롤백된 경우 등).
        //          남길 기록이 있으니 취소가 아니라 완료로 닫아야 결과 조회가 이어진다
        givenRoom(() -> room(RunningRoomStatus.STARTED, RUNNER));
        givenPlayer(RUNNER, RunningPlayerStatus.COMPLETED);
        given(existsRunningRecordPort.existsInRoom(new RunningRoomId(ROOM_ID)))
                .willReturn(true);

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then
        assertThat(lastUpdatedRoom().getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        assertThat(lastUpdatedRoom().getCloseAt()).isPresent();
        verify(runningComboArchiver).archiveAfterCommit(ROOM_ID);
    }

    @Test
    @DisplayName("이미 닫힌 방에 남은 미출석자도 끝까지 닫는다")
    void closesNoShowLeftBehindInClosedRoom() {
        // given -> 뛴 사람이 정상 종료해 방은 이미 FINISHED다. 그런데 안 나타난 사람은
        //          RUNNING이 아니라 종료 판정에 세어지지 않아 활성 신청으로 남는다.
        //          여기서 닫지 않으면 그 사람은 다음 러닝을 영영 못 한다
        givenRoom(() -> finishedRoom(NO_SHOW));
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 신청은 닫히고, 이미 종료된 방은 그대로 둔다
        assertThat(updatedPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        assertThat(lastUpdatedRoom().getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        verifyNoInteractions(existsRunningRecordPort);
    }

    @Test
    @DisplayName("방이 없으면 아무것도 하지 않고 완료로 답한다")
    void skipsWhenRoomIsGone() {
        // given -> 예약만 남고 방이 사라진 경우. 예약을 소비한 것으로 보고 끝낸다
        given(loadRunningRoomPort.loadById(new RunningRoomId(ROOM_ID)))
                .willReturn(Optional.empty());

        // when
        Optional<LocalDateTime> retryAt =
                forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then
        assertThat(retryAt).isEmpty();
        verifyNoInteractions(lockRunningPlayerPort, lockRunningRoomPort, updateRunningRoomPort,
                updateRunningPlayerPort, startMatchCooldownPort, finishRunningUsecase);
    }

    @Test
    @DisplayName("뛰는 사람만 남았으면 잠그지 않고 워커로 넘긴 뒤 1분 뒤로 다시 걸어 달라고 답한다 (D-83)")
    void dispatchesRunnersWithoutLock() {
        // given -> 미출석자도 없고 방도 고칠 것이 없다
        givenSnapshot(room(RunningRoomStatus.STARTED, RUNNER));
        givenPlayer(RUNNER, RunningPlayerStatus.RUNNING);

        // when
        Optional<LocalDateTime> retryAt =
                forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 확정은 워커가 일반 종료로 한다. 끝났는지는 다음 알람이 DB로 본다
        verifyNoInteractions(lockRunningPlayerPort, lockRunningRoomPort);
        verify(finishRunningUsecase).handle(new FinishRunningCommand(ROOM_ID, RUNNER.value(), true));
        assertThat(retryAt).hasValueSatisfying(at -> assertThat(at)
                .isCloseTo(LocalDateTime.now().plus(RETRY_INTERVAL), within(5, ChronoUnit.SECONDS)));
    }

    @Test
    @DisplayName("다시 걸기 기간을 넘기면 러너가 남아도 포기하고 완료로 답한다")
    void givesUpAfterRetryWindow() {
        // given -> 시작 + 6시간 + 7시간을 넘었다. 트랙도 없어 상태만 확정될 러너도 못 끝냈다
        givenSnapshot(buildRoom(RunningRoomType.MATCH, RunningRoomStatus.STARTED, null, true,
                LocalDateTime.now().minusHours(14), RUNNER));
        givenPlayer(RUNNER, RunningPlayerStatus.RUNNING);

        // when
        Optional<LocalDateTime> retryAt =
                forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 1분마다 영원히 돌지 않는다
        assertThat(retryAt).isEmpty();
        verifyNoInteractions(finishRunningUsecase);
    }

    @Test
    @DisplayName("러너 확정이 실패해도 던지지 않고 다음 알람에 맡긴다")
    void keepsRetryWhenRunnerFails() {
        // given -> S3 장애 등으로 이 러너를 끝내지 못했다
        givenSnapshot(room(RunningRoomStatus.STARTED, RUNNER));
        givenPlayer(RUNNER, RunningPlayerStatus.RUNNING);
        willThrow(new IllegalStateException("S3 down")).given(finishRunningUsecase).handle(any());

        // when
        Optional<LocalDateTime> retryAt =
                forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 던지면 워커 스레드에서 사라진다. 예약은 다시 걸려 있다
        assertThat(retryAt).isPresent();
    }

    @Test
    @DisplayName("본인 종료가 같은 러너를 끝내는 중이면 건너뛰고 다음 알람에 맡긴다")
    void skipsRunnerFinishingThemselves() {
        // given -> 6시간 정각에 러너가 직접 종료를 눌러 끝내기 임대를 쥐고 있다
        givenSnapshot(room(RunningRoomStatus.STARTED, RUNNER));
        givenPlayer(RUNNER, RunningPlayerStatus.RUNNING);
        willThrow(new RunningFinishInProgressException()).given(finishRunningUsecase).handle(any());

        // when
        Optional<LocalDateTime> retryAt =
                forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 방 전체가 롤백되지 않는다. 본인 확정이 끝내거나 다음 알람이 다시 본다
        assertThat(retryAt).isPresent();
    }

    @Test
    @DisplayName("잠금 없이 본 미출석자가 잠그기 전에 뛰기 시작했으면 이탈시키지 않는다")
    void doesNotLeavePlayerWhoStartedBetween() {
        // given -> 스냅숏에서는 JOINED, 잠그고 다시 읽으니 RUNNING_START가 먼저 들어와 RUNNING이다
        givenRoom(() -> room(RunningRoomStatus.STARTED, NO_SHOW));
        given(loadRoomPlayerPort.load(new RunningRoomId(ROOM_ID), NO_SHOW))
                .willReturn(Optional.of(player(NO_SHOW, RunningPlayerStatus.JOINED)),
                        Optional.of(player(NO_SHOW, RunningPlayerStatus.RUNNING)));

        // when
        Optional<LocalDateTime> retryAt =
                forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 잠금 없이 읽은 값으로 고치지 않는다 — 뛰는 사람으로 워커에 넘긴다
        verify(updateRunningPlayerPort, never()).update(any());
        verifyNoInteractions(startMatchCooldownPort);
        verify(finishRunningUsecase).handle(new FinishRunningCommand(ROOM_ID, NO_SHOW.value(), true));
        assertThat(retryAt).isPresent();
    }

    // 잠금 없이 본 방과 잠그고 다시 읽은 방은 다른 인스턴스다 — 잠금 안 단계가 고친 것이 스냅숏에 새면 안 된다
    private void givenRoom(Supplier<RunningRoom> room) {
        givenSnapshot(room.get());
        lenient().when(lockRunningRoomPort.lockById(new RunningRoomId(ROOM_ID)))
                .thenReturn(Optional.of(room.get()));
    }

    private void givenSnapshot(RunningRoom room) {
        given(loadRunningRoomPort.loadById(new RunningRoomId(ROOM_ID)))
                .willReturn(Optional.of(room));
    }

    private void givenPlayer(UserId userId, RunningPlayerStatus status) {
        given(loadRoomPlayerPort.load(new RunningRoomId(ROOM_ID), userId))
                .willAnswer(invocation -> Optional.of(player(userId, status)));
    }

    private RunningPlayer updatedPlayer() {
        List<RunningPlayer> updated = updatedPlayers();
        assertThat(updated).hasSize(1);
        return updated.get(0);
    }

    private List<RunningPlayer> updatedPlayers() {
        ArgumentCaptor<RunningPlayer> captor = ArgumentCaptor.forClass(RunningPlayer.class);
        verify(updateRunningPlayerPort, atLeastOnce()).update(captor.capture());
        return captor.getAllValues();
    }

    private RunningRoom lastUpdatedRoom() {
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(updateRunningRoomPort, atLeastOnce()).update(captor.capture());
        return captor.getValue();
    }

    private static RunningPlayer player(UserId userId, RunningPlayerStatus status) {
        boolean finished = status == RunningPlayerStatus.COMPLETED;
        return RunningPlayer.builder()
                .runningPlayerId(7L)
                .userId(userId.value())
                .status(status)
                .avgPace(AVG_PACE)
                .targetDistance(TARGET_DISTANCE)
                .startAt(LocalDateTime.now().minusHours(6))
                // 종료 시각이 찍힌 참가자 = 이미 확정이 끝난 참가자다
                .deletedAt(finished ? LocalDateTime.now().minusMinutes(5) : null)
                .build();
    }

    private static RunningRoom room(RunningRoomStatus status, UserId... members) {
        return buildRoom(RunningRoomType.MATCH, status, null, true, LocalDateTime.now().minusHours(6), members);
    }

    private static RunningRoom inviteRoom(RunningRoomStatus status, UserId... members) {
        return buildRoom(RunningRoomType.INVITE, status, null, true, LocalDateTime.now().minusHours(6), members);
    }

    // 뛴 사람이 정상 종료해 이미 닫힌 방 — 미출석자의 세션만 아직 붙어 있다
    private static RunningRoom finishedRoom(UserId... members) {
        return buildRoom(RunningRoomType.MATCH, RunningRoomStatus.FINISHED, LocalDateTime.now(), true,
                LocalDateTime.now().minusHours(6), members);
    }

    // 모두 끝나 자리까지 비운 방
    private static RunningRoom finishedRoomWithoutSessions() {
        return buildRoom(RunningRoomType.MATCH, RunningRoomStatus.FINISHED, LocalDateTime.now(), false,
                LocalDateTime.now().minusHours(6), RUNNER, NO_SHOW);
    }

    static RunningRoom buildRoom(RunningRoomType type, RunningRoomStatus status, LocalDateTime closeAt,
                                 boolean connected, LocalDateTime startAt, UserId... members) {
        List<SessionDraft> sessions = new ArrayList<>();
        long playerId = 7L;
        for (UserId member : members) {
            sessions.add(new SessionDraft(member, new RunningPlayerId(playerId++), 0, connected));
        }
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(type)
                .status(status)
                // 아무도 취소·탈퇴하지 않은 방이라 확정 인원 그대로다 — 제재 판정이 이 값을 본다
                .currentPlayerCount(members.length)
                .maxPlayerCount(4)
                .startAt(startAt)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .sessions(sessions)
                .closeAt(closeAt)
                .build();
    }
}
