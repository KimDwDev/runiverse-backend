package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.MatchProperties;
import com.runiverse.running_service.application.match.command.apply.MatchRoomAssigner;
import com.runiverse.running_service.application.match.port.out.CreateMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchCandidatesPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchPlayersPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchCandidate;
import com.runiverse.running_service.application.match.port.out.MatchPlayer;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 방 배정 단위 테스트")
class MatchRoomAssignerTest {

    private static final UserId APPLICANT = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final RunningPlayerId APPLICATION = new RunningPlayerId(7L);
    private static final Pace MY_PACE = new Pace(360);            // 6분/km
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 7, 25, 19, 0);
    private static final int TARGET_DISTANCE = 5_000;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(15);
    // 페이스 차가 이 값 이내면 동급으로 보고 leave_count가 순위를 가른다
    private static final int PACE_TIE_TOLERANCE = 10;
    private static final long NEW_ROOM_ID = 999L;

    @Mock
    private LoadMatchCandidatesPort loadMatchCandidatesPort;

    @Mock
    private LockMatchRoomPort lockMatchRoomPort;

    @Mock
    private LoadMatchPlayersPort loadMatchPlayersPort;

    @Mock
    private UpdateMatchRoomPort updateMatchRoomPort;

    @Mock
    private CreateMatchRoomPort createMatchRoomPort;

    private MatchRoomAssigner matchRoomAssigner;

    @BeforeEach
    void setUp() {
        matchRoomAssigner = new MatchRoomAssigner(
                loadMatchCandidatesPort, lockMatchRoomPort, loadMatchPlayersPort,
                updateMatchRoomPort, createMatchRoomPort,
                new MatchProperties(CLOSE_OFFSET, PACE_TIE_TOLERANCE));
    }

    @Test
    @DisplayName("후보가 없으면 1인 방을 새로 연다")
    void opensNewRoomWhenNoCandidate() {
        // given -> "방 미배정" 상태는 없다 — 붙을 방이 없으면 만들어서라도 배정한다(feature-spec)
        givenCandidates();
        given(createMatchRoomPort.create(any())).willReturn(savedRoom(NEW_ROOM_ID));

        // when
        RunningRoomId assigned = assign();

        // then
        assertThat(assigned.value()).isEqualTo(NEW_ROOM_ID);
        verifyNoInteractions(lockMatchRoomPort, updateMatchRoomPort);
    }

    @Test
    @DisplayName("새 방은 신청자의 페이스·거리·슬롯을 그대로 갖는다")
    void newRoomInheritsApplicantCondition() {
        // given
        givenCandidates();
        given(createMatchRoomPort.create(any())).willReturn(savedRoom(NEW_ROOM_ID));

        // when
        assign();

        // then -> 1인 방은 창설자 페이스가 곧 방 평균이다
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(createMatchRoomPort).create(captor.capture());
        RunningRoom created = captor.getValue();
        assertThat(created.getType()).isEqualTo(RunningRoomType.MATCH);
        assertThat(created.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
        assertThat(created.getStartAt()).isEqualTo(START_AT);
        assertThat(created.getAvgPace()).map(Pace::secondsPerKm).contains(MY_PACE.secondsPerKm());
        assertThat(created.getPlayerCount().current()).isOne();
        assertThat(created.getPlayerCount().max()).isEqualTo(4);
    }

    @Test
    @DisplayName("페이스 차가 30초/km를 넘는 방은 후보에서 걸러진다")
    void skipsCandidateOutOfPaceRange() {
        // given -> 자격의 정본은 도메인(Pace.isCloseTo)이지만 헛된 잠금을 줄이려 미리 거른다
        givenCandidates(candidate(1L, MY_PACE.secondsPerKm() + 31, 0));
        given(createMatchRoomPort.create(any())).willReturn(savedRoom(NEW_ROOM_ID));

        // when
        RunningRoomId assigned = assign();

        // then -> 잠글 후보가 없어 새 방으로 간다
        assertThat(assigned.value()).isEqualTo(NEW_ROOM_ID);
        verifyNoInteractions(lockMatchRoomPort);
    }

    @Test
    @DisplayName("페이스가 가장 가까운 방을 고른다")
    void picksClosestPace() {
        // given -> 셋 다 ±30초 안이지만 차이가 다르다. 순서는 뒤섞어 둔다
        givenCandidates(
                candidate(1L, MY_PACE.secondsPerKm() + 25, 0),
                candidate(2L, MY_PACE.secondsPerKm() + 3, 0),
                candidate(3L, MY_PACE.secondsPerKm() - 14, 0));
        givenJoinable(2L);

        // when
        RunningRoomId assigned = assign();

        // then
        assertThat(assigned.value()).isEqualTo(2L);
    }

    @Test
    @DisplayName("페이스가 비슷하면 이탈이 적었던 방을 고른다")
    void breaksPaceTieByLeaveCount() {
        // given -> 차이가 3초와 9초로 임계(10초) 안이라 동급이다.
        //          그러면 사람들이 잘 떠나지 않은 방이 이긴다(feature-spec 방 배정 기준)
        givenCandidates(
                candidate(1L, MY_PACE.secondsPerKm() + 3, 5),
                candidate(2L, MY_PACE.secondsPerKm() + 9, 1));
        givenJoinable(2L);

        // when
        RunningRoomId assigned = assign();

        // then -> 페이스만 보면 1번이 가깝지만 동급 구간이라 leave_count가 갈랐다
        assertThat(assigned.value()).isEqualTo(2L);
    }

    @Test
    @DisplayName("페이스 차가 임계를 넘으면 이탈 횟수보다 페이스가 우선한다")
    void paceWinsOverLeaveCountBeyondTolerance() {
        // given -> 2초와 20초는 임계(10초)를 사이에 두고 갈린다
        givenCandidates(
                candidate(1L, MY_PACE.secondsPerKm() + 2, 9),
                candidate(2L, MY_PACE.secondsPerKm() + 20, 0));
        givenJoinable(1L);

        // when
        RunningRoomId assigned = assign();

        // then
        assertThat(assigned.value()).isEqualTo(1L);
    }

    @Test
    @DisplayName("그새 자리가 찬 방은 건너뛰고 다음 후보로 간다")
    void fallsBackToNextCandidateWhenRoomFilledUp() {
        // given -> 스캔은 잠금 없이 했다. 1번은 그 사이 정원이 찼다
        givenCandidates(
                candidate(1L, MY_PACE.secondsPerKm(), 0),
                candidate(2L, MY_PACE.secondsPerKm() + 5, 0));
        given(lockMatchRoomPort.lockById(new RunningRoomId(1L)))
                .willReturn(Optional.of(room(1L, MY_PACE.secondsPerKm(), 4)));
        givenJoinable(2L);

        // when
        RunningRoomId assigned = assign();

        // then
        assertThat(assigned.value()).isEqualTo(2L);
        verifyNoInteractions(createMatchRoomPort);
    }

    @Test
    @DisplayName("후보가 전부 막히면 결국 새 방을 연다")
    void opensNewRoomWhenEveryCandidateRejects() {
        // given -> 잠근 사이 마감돼 모집 상태가 아니게 된 방
        givenCandidates(candidate(1L, MY_PACE.secondsPerKm(), 0));
        given(lockMatchRoomPort.lockById(new RunningRoomId(1L)))
                .willReturn(Optional.of(closedRoom(1L)));
        given(createMatchRoomPort.create(any())).willReturn(savedRoom(NEW_ROOM_ID));

        // when
        RunningRoomId assigned = assign();

        // then
        assertThat(assigned.value()).isEqualTo(NEW_ROOM_ID);
        verifyNoInteractions(updateMatchRoomPort);
    }

    @Test
    @DisplayName("합류하면 기존 참가자와 신청자를 합쳐 방 평균 페이스를 다시 계산한다")
    void recalculatesRoomAveragePaceAfterJoin() {
        // given -> 기존 참가자 380·340, 신청자 360 → 평균 360
        givenCandidates(candidate(1L, 360, 0));
        given(lockMatchRoomPort.lockById(new RunningRoomId(1L)))
                .willReturn(Optional.of(room(1L, 360, 2)));
        given(loadMatchPlayersPort.loadPlayers(new RunningRoomId(1L))).willReturn(List.of(
                new MatchPlayer(UuidCreator.getTimeOrderedEpoch(), 380),
                new MatchPlayer(UuidCreator.getTimeOrderedEpoch(), 340)));

        // when
        assign();

        // then -> 합류자의 세션은 아직 저장 전이라 조회에 안 잡힌다. 빠뜨리면 평균이 틀어진다
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(updateMatchRoomPort).update(captor.capture());
        RunningRoom joined = captor.getValue();
        assertThat(joined.getAvgPace()).map(Pace::secondsPerKm).contains(360);
        assertThat(joined.getPlayerCount().current()).isEqualTo(3);
    }

    @Test
    @DisplayName("전에 나갔던 방에 다시 배정되면 세션을 새로 만들지 않고 되살린다")
    void revivesSessionWhenReassignedToPreviousRoom() {
        // given -> 취소해서 is_connected=false로 남아 있던 세션. 키가 유저라 행이 하나다(erd)
        givenCandidates(candidate(1L, MY_PACE.secondsPerKm(), 1));
        given(lockMatchRoomPort.lockById(new RunningRoomId(1L)))
                .willReturn(Optional.of(roomWithLeftSession(1L)));
        given(loadMatchPlayersPort.loadPlayers(new RunningRoomId(1L))).willReturn(List.of());

        // when
        assign();

        // then -> 이탈 이력(leave_count)은 그대로 남고 신청만 새것으로 갈린다
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(updateMatchRoomPort).update(captor.capture());
        assertThat(captor.getValue().getSessions()).hasSize(1);
        var session = captor.getValue().getSessions().getFirst();
        assertThat(session.isSameUser(APPLICANT)).isTrue();
        assertThat(session.isConnected()).isTrue();
        assertThat(session.getRunningPlayerId()).isEqualTo(APPLICATION);
        assertThat(session.getLeaveCount().value()).isEqualTo(1);
    }

    private RunningRoomId assign() {
        return matchRoomAssigner.assign(
                APPLICANT, APPLICATION, MY_PACE, START_AT, TARGET_DISTANCE);
    }

    private void givenCandidates(MatchCandidate... candidates) {
        given(loadMatchCandidatesPort.loadCandidates(START_AT, TARGET_DISTANCE))
                .willReturn(List.of(candidates));
    }

    // 자리가 남은 방을 잠금 조회에 물려 둔다 — 참가자 조회는 평균 재계산에서만 쓴다
    private void givenJoinable(long roomId) {
        given(lockMatchRoomPort.lockById(new RunningRoomId(roomId)))
                .willReturn(Optional.of(room(roomId, MY_PACE.secondsPerKm(), 1)));
        given(loadMatchPlayersPort.loadPlayers(new RunningRoomId(roomId))).willReturn(List.of());
    }

    private static MatchCandidate candidate(long roomId, int avgPace, long totalLeaveCount) {
        return new MatchCandidate(roomId, avgPace, totalLeaveCount);
    }

    // 모집 중인 방 — 세션은 이미 있는 다른 참가자의 것이다
    private static RunningRoom room(long roomId, int avgPace, int currentPlayerCount) {
        return RunningRoom.builder()
                .runningRoomId(roomId)
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.MATCHING)
                .startAt(START_AT)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(avgPace)
                .currentPlayerCount(currentPlayerCount)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(
                        new UserId(UuidCreator.getTimeOrderedEpoch()),
                        new RunningPlayerId(1L), 0, true)))
                .build();
    }

    // 신청자가 전에 이 방에 있다 나간 상태 — 인원에는 안 잡히고 세션만 남아 있다
    private static RunningRoom roomWithLeftSession(long roomId) {
        return RunningRoom.builder()
                .runningRoomId(roomId)
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.MATCHING)
                .startAt(START_AT)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(MY_PACE.secondsPerKm())
                .currentPlayerCount(0)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(
                        APPLICANT, new RunningPlayerId(3L), 1, false)))
                .build();
    }

    // 잠근 사이 마감돼 더는 모집하지 않는 방
    private static RunningRoom closedRoom(long roomId) {
        return RunningRoom.builder()
                .runningRoomId(roomId)
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.MATCHED)
                .startAt(START_AT)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(MY_PACE.secondsPerKm())
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(
                        new UserId(UuidCreator.getTimeOrderedEpoch()),
                        new RunningPlayerId(1L), 0, true)))
                .build();
    }

    private static RunningRoom savedRoom(long roomId) {
        return RunningRoom.builder()
                .runningRoomId(roomId)
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.MATCHING)
                .startAt(START_AT)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(MY_PACE.secondsPerKm())
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(
                        APPLICANT, APPLICATION, 0, true)))
                .build();
    }
}
