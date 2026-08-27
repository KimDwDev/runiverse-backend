package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.finish.FinishRunningHandler;
import com.runiverse.running_service.application.running.command.finish.RunningFinishProperties;
import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningNotStartableException;
import com.runiverse.running_service.application.running.exception.RunningRoomNotFoundException;
import com.runiverse.running_service.application.running.port.out.DeleteRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadUserWeightPort;
import com.runiverse.running_service.application.running.port.out.RunningTrack;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 종료 단위 테스트")
public class FinishRunningHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long PLAYER_ID = 42L;
    private static final long ROOM_ID = 125L;
    private static final int AVG_PACE = 330;                              // 5분 30초/km
    private static final int TARGET = 5_000;
    private static final BigDecimal WEIGHT = new BigDecimal("70.0");
    private static final LocalDateTime PAST = LocalDateTime.now().minusMinutes(30);
    private static final LocalDateTime TRACK_START = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    // TrackDistance와 같은 지구 반경에서 뽑는다 — 어긋나면 의도한 거리와 측정 거리가 벌어져
    // 80% 경계 테스트가 엉뚱한 쪽으로 넘어간다
    private static final double METERS_PER_DEGREE = Math.toRadians(1) * 6_371_008.8;

    // 운영 설정 그대로 — 페널티 경계는 목표의 80%다
    private static final RunningFinishProperties PROPERTIES = new RunningFinishProperties(
            0.8, 10, 100, 60, 3.0);

    @Mock
    private LoadRunningRoomPort loadRunningRoomPort;

    @Mock
    private LoadRoomPlayerPort loadRoomPlayerPort;

    @Mock
    private LoadRunningTrackPort loadRunningTrackPort;

    @Mock
    private LoadUserWeightPort loadUserWeightPort;

    @Mock
    private UpdateRunningPlayerPort updateRunningPlayerPort;

    @Mock
    private DeleteRunningTrackPort deleteRunningTrackPort;

    private FinishRunningHandler handler;

    // 설정값은 검증 대상이라 mock이 아니라 실제 값을 넣는다 — 0.8 경계가 이 테스트의 주제다
    @BeforeEach
    void setUp() {
        handler = new FinishRunningHandler(loadRunningRoomPort, loadRoomPlayerPort,
                loadRunningTrackPort, loadUserWeightPort, updateRunningPlayerPort,
                deleteRunningTrackPort, PROPERTIES);
    }

    // 종료 시각이 찍힌 참가자 = 이미 확정이 끝난 참가자다(deleted_at이 곧 종료 표시)
    private static RunningPlayer player(RunningPlayerStatus status, LocalDateTime deletedAt) {
        return RunningPlayer.builder()
                .runningPlayerId(PLAYER_ID)
                .userId(USER_ID)
                .status(status)
                .avgPace(AVG_PACE)
                .targetDistance(TARGET)
                .startAt(PAST)
                .deletedAt(deletedAt)
                .build();
    }

    private static RunningRoom room(RunningRoomType type, Integer targetDistance) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(type)
                .status(RunningRoomStatus.STARTED)
                .startAt(PAST)
                .targetDistance(targetDistance)
                .avgPace(AVG_PACE)
                .currentPlayerCount(1)
                .maxPlayerCount(type == RunningRoomType.SOLO ? 1 : 4)
                .sessions(List.of(new SessionDraft(new RunningPlayerId(PLAYER_ID), 0, true)))
                .build();
    }

    // 북쪽으로 초당 stepMeters씩 달리는 트랙 — (count - 1) * step 미터가 실측 거리다
    private static RunningTrack track(int count, double stepMeters) {
        List<TrackPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new TrackPoint(i, 37.5 + i * stepMeters / METERS_PER_DEGREE, 127.0,
                    null, 5.0, null, null, 168, null, TRACK_START.plusSeconds(i)));
        }
        return new RunningTrack("raw", points);
    }

    private void givenPlayer(RunningPlayer player) {
        given(loadRoomPlayerPort.load(new RunningRoomId(ROOM_ID), new UserId(USER_ID)))
                .willReturn(Optional.ofNullable(player));
    }

    private void givenRoom(RunningRoom room) {
        given(loadRunningRoomPort.loadById(new RunningRoomId(ROOM_ID))).willReturn(Optional.of(room));
    }

    private void givenTrack(RunningTrack track) {
        given(loadUserWeightPort.loadWeightKg(new UserId(USER_ID))).willReturn(Optional.of(WEIGHT));
        given(loadRunningTrackPort.load(ROOM_ID, new UserId(USER_ID))).willReturn(track);
    }

    private void finish() {
        handler.handle(new FinishRunningCommand(ROOM_ID, USER_ID, false));
    }

    @Nested
    @DisplayName("멱등 테스트")
    class IdempotencyTest {

        @Test
        @DisplayName("이미 완주 확정된 참가자는 다시 확정하지 않고 넘어간다")
        void skipsAlreadyCompletedPlayer() {
            // given -> 앞선 요청이나 타임아웃이 먼저 확정해 둔 상태
            givenPlayer(player(RunningPlayerStatus.COMPLETED, PAST.plusMinutes(20)));

            // when & then -> 도메인 상태 전이를 다시 시도하면 500이 된다
            assertThatCode(FinishRunningHandlerTest.this::finish).doesNotThrowAnyException();
            verifyNoInteractions(updateRunningPlayerPort);
        }

        @Test
        @DisplayName("이미 이탈 확정된 참가자도 같은 경로를 탄다")
        void skipsAlreadyLeftPlayer() {
            // given
            givenPlayer(player(RunningPlayerStatus.RUNNING_LEFT_PENALTY, PAST.plusMinutes(10)));

            // when & then
            assertThatCode(FinishRunningHandlerTest.this::finish).doesNotThrowAnyException();
            verifyNoInteractions(updateRunningPlayerPort);
        }

        @Test
        @DisplayName("확정을 건너뛰어도 로컬 트랙 정리를 위해 버퍼는 비운다")
        void deletesTrackOnRepeatedFinish() {
            // given
            givenPlayer(player(RunningPlayerStatus.COMPLETED, PAST.plusMinutes(20)));

            // when -> 클라가 ack를 못 받아 다시 보낸 상황
            finish();

            // then -> 여기서 안 지우면 재전송 클라의 버퍼가 TTL까지 남는다
            verify(deleteRunningTrackPort).delete(ROOM_ID, new UserId(USER_ID));
        }
    }

    @Nested
    @DisplayName("상태 확정 테스트")
    class StatusTest {

        @Test
        @DisplayName("목표 거리를 채우면 완주다")
        void completesWhenTargetReached() {
            // given -> 약 5,040m를 뛰어 목표를 넘겼다
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(track(1_801, 2.8));

            // when
            finish();

            // then
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.COMPLETED);
            assertThat(player.getDeletedAt()).isPresent();
        }

        @Test
        @DisplayName("목표의 80%를 채운 조기 종료는 페널티가 없다")
        void leavesWithoutPenaltyAtRatioBoundary() {
            // given -> 4,002.5m를 뛰어 구간 경계가 정확히 4,000m에서 끊긴다 = 목표의 80%
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(track(1_602, 2.5));

            // when
            finish();

            // then -> 경계값은 페널티가 아니다(비율 미만일 때만 제재)
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.RUNNING_LEFT_NO_PENALTY);
        }

        @Test
        @DisplayName("목표의 80%에 못 미치면 페널티가 붙는다")
        void leavesWithPenaltyBelowRatio() {
            // given -> 3,992.5m를 뛰어 3,990m에서 끊긴다 = 79.8%로 경계 바로 아래
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(track(1_598, 2.5));

            // when
            finish();

            // then
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.RUNNING_LEFT_PENALTY);
        }

        @Test
        @DisplayName("기록을 만들 수 없는 트랙이면 실제 거리를 0으로 판정한다")
        void treatsUnusableTrackAsZeroDistance() {
            // given -> 좌표가 하나도 안 올라온 러닝
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(new RunningTrack("", List.of()));

            // when -> 상태는 확정한다. 기록만 남기지 않는다(feature-spec §2)
            finish();

            // then
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.RUNNING_LEFT_PENALTY);
        }

        @Test
        @DisplayName("목표가 없는 솔로 러닝은 사용자가 끝내면 완주다")
        void completesSoloWithoutTarget() {
            // given -> 솔로 방은 target_distance가 null이라 비율을 잴 분모가 없다
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.SOLO, null));
            givenTrack(track(1_601, 2.5));

            // when
            finish();

            // then
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.COMPLETED);
        }

        @Test
        @DisplayName("확정한 참가자는 되돌려 쓰고 트랙 버퍼를 비운다")
        void writesBackPlayerAndClearsTrack() {
            // given
            RunningPlayer player = player(RunningPlayerStatus.RUNNING, null);
            givenPlayer(player);
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            givenTrack(track(1_801, 2.8));

            // when
            finish();

            // then -> 이 호출이 빠지면 상태 전이가 통째로 사라진다
            verify(updateRunningPlayerPort).update(player);
            verify(deleteRunningTrackPort).delete(ROOM_ID, new UserId(USER_ID));
        }
    }

    @Nested
    @DisplayName("거부 테스트")
    class RejectTest {

        @Test
        @DisplayName("이 방의 참가자가 아니면 거부한다")
        void rejectNonRoomPlayer() {
            // given -> 남의 방 번호를 실어 보냈다
            givenPlayer(null);

            // when & then
            assertThatThrownBy(FinishRunningHandlerTest.this::finish)
                    .isInstanceOf(NotRoomPlayerException.class);
            verifyNoInteractions(updateRunningPlayerPort, deleteRunningTrackPort);
        }

        @Test
        @DisplayName("RUNNING_START를 거치지 않은 참가자는 확정할 러닝이 없다")
        void rejectNotStartedPlayer() {
            // given -> 배정만 받고 시작 메시지는 보내지 않았다
            givenPlayer(player(RunningPlayerStatus.JOINED, null));

            // when & then -> 도메인 예외로 새면 500이라 여기서 거른다
            assertThatThrownBy(FinishRunningHandlerTest.this::finish)
                    .isInstanceOf(RunningNotStartableException.class);
            verifyNoInteractions(updateRunningPlayerPort, deleteRunningTrackPort);
        }

        @Test
        @DisplayName("수락하지 않은 초대도 종료 대상이 아니다")
        void rejectInvitedPlayer() {
            // given
            givenPlayer(player(RunningPlayerStatus.INVITED, null));

            // when & then
            assertThatThrownBy(FinishRunningHandlerTest.this::finish)
                    .isInstanceOf(RunningNotStartableException.class);
            verifyNoInteractions(updateRunningPlayerPort, deleteRunningTrackPort);
        }

        @Test
        @DisplayName("참가자는 있는데 방이 없으면 거부한다")
        void rejectUnknownRoom() {
            // given -> 목표 거리를 정하는 쪽이 방이라 방 없이는 판정할 수 없다
            givenPlayer(player(RunningPlayerStatus.RUNNING, null));
            given(loadRunningRoomPort.loadById(new RunningRoomId(ROOM_ID)))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(FinishRunningHandlerTest.this::finish)
                    .isInstanceOf(RunningRoomNotFoundException.class);
            verifyNoInteractions(updateRunningPlayerPort, deleteRunningTrackPort);
        }

        @Test
        @DisplayName("체중이 없으면 온보딩을 마치지 않은 사용자다")
        void rejectWithoutWeight() {
            // given -> 온보딩은 몸무게가 필수라 여기까지 오면 데이터가 어긋난 것이다
            givenPlayer(player(RunningPlayerStatus.RUNNING, null));
            givenRoom(room(RunningRoomType.MATCH, TARGET));
            given(loadUserWeightPort.loadWeightKg(new UserId(USER_ID))).willReturn(Optional.empty());

            // when & then -> 칼로리를 지어내느니 확정을 멈춘다
            assertThatThrownBy(FinishRunningHandlerTest.this::finish)
                    .isInstanceOf(OnboardingNotCompletedException.class);
            verifyNoInteractions(updateRunningPlayerPort, deleteRunningTrackPort);
        }
    }
}
