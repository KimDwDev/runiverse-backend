package com.runiverse.running_service.unit_test.running.domain.room;

import com.runiverse.running_service.domain.running.room.exception.InvalidRoomStatusTransitionException;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RunningRoomStatusTest {

    // 명세로서의 전이표 — 구현과 따로 적어 둔다. 둘이 어긋나면 테스트가 깨진다.
    private static final Map<RunningRoomStatus, Set<RunningRoomStatus>> ALLOWED =
            new EnumMap<>(Map.of(
                    RunningRoomStatus.MATCHING,
                    EnumSet.of(RunningRoomStatus.MATCHED, RunningRoomStatus.CANCELLED),
                    RunningRoomStatus.MATCHED,
                    EnumSet.of(RunningRoomStatus.STARTED, RunningRoomStatus.CANCELLED),
                    RunningRoomStatus.STARTED,
                    EnumSet.of(RunningRoomStatus.FINISHED, RunningRoomStatus.CANCELLED),
                    RunningRoomStatus.FINISHED,
                    EnumSet.noneOf(RunningRoomStatus.class),
                    RunningRoomStatus.CANCELLED,
                    EnumSet.noneOf(RunningRoomStatus.class)));

    private static Stream<Arguments> allowedPairs() {
        return ALLOWED.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(next -> Arguments.of(entry.getKey(), next)));
    }

    private static Stream<Arguments> deniedPairs() {
        return Arrays.stream(RunningRoomStatus.values())
                .flatMap(current -> Arrays.stream(RunningRoomStatus.values())
                        .filter(next -> !ALLOWED.get(current).contains(next))
                        .map(next -> Arguments.of(current, next)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allowedPairs")
    @DisplayName("전이표에 있는 조합은 다음 상태를 돌려준다")
    void transitionToAllowedStatusSuccess(RunningRoomStatus current, RunningRoomStatus next) {
        // when & then
        assertThat(current.canTransitionTo(next)).isTrue();
        assertThat(current.transitionTo(next)).isEqualTo(next);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("deniedPairs")
    @DisplayName("전이표에 없는 조합은 예외가 발생한다")
    void transitionToDeniedStatusFails(RunningRoomStatus current, RunningRoomStatus next) {
        // when & then -> 취소된 방이 다시 시작되거나 모집을 건너뛰고 달리는 것을 막는다
        assertThat(current.canTransitionTo(next)).isFalse();
        assertThatThrownBy(() -> current.transitionTo(next))
                .isInstanceOf(InvalidRoomStatusTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(RunningRoomStatus.class)
    @DisplayName("같은 상태로는 전이할 수 없다")
    void transitionToSelfFails(RunningRoomStatus current) {
        // when & then -> 중복 이벤트가 같은 상태를 다시 쓰지 않게 한다
        assertThatThrownBy(() -> current.transitionTo(current))
                .isInstanceOf(InvalidRoomStatusTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = RunningRoomStatus.class, names = {"FINISHED", "CANCELLED"})
    @DisplayName("종료·취소는 종착 상태다")
    void finishedAndCancelledAreTerminal(RunningRoomStatus current) {
        // when & then
        assertThat(current.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = RunningRoomStatus.class, names = {"FINISHED", "CANCELLED"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("진행 중인 상태는 종착 상태가 아니다")
    void inProgressStatusesAreNotTerminal(RunningRoomStatus current) {
        // when & then
        assertThat(current.isTerminal()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = RunningRoomStatus.class, names = {"MATCHING", "MATCHED", "STARTED"})
    @DisplayName("종료 전 어느 단계에서든 취소할 수 있다")
    void cancellableFromEveryOngoingStatus(RunningRoomStatus current) {
        // when & then -> 솔로가 기록 없이 그만두는 경우와 전원 이탈을 담는 경로다
        assertThat(current.canTransitionTo(RunningRoomStatus.CANCELLED)).isTrue();
    }
}
