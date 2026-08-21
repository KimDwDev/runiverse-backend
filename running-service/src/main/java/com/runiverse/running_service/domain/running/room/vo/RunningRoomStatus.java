package com.runiverse.running_service.domain.running.room.vo;

import com.runiverse.running_service.domain.running.room.exception.InvalidRoomStatusTransitionException;

import java.util.Set;

public enum RunningRoomStatus {
    MATCHING,
    MATCHED,
    STARTED,
    FINISHED,
    CANCELLED;

    // 애그리거트가 상태를 바꾸기 전에 호출 -> 위반은 여기서 잡는다.
    public RunningRoomStatus transitionTo(RunningRoomStatus next) {
        if (!canTransitionTo(next)) {
            throw new InvalidRoomStatusTransitionException();
        }
        return next;
    }

    // 변경이 될 수 있는 상태 변화 인지 체크
    public boolean canTransitionTo(RunningRoomStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    // 러닝 시작 전인지 — 빈 방을 닫을 수 있는 구간이다
    public boolean isBeforeStart() {
        return this == MATCHING || this == MATCHED;
    }

    private Set<RunningRoomStatus> allowedNext() {
        return switch (this) {
            case MATCHING -> Set.of(MATCHED, CANCELLED);
            case MATCHED -> Set.of(STARTED, CANCELLED);
            // 시작한 방은 취소하지 않는다 — 닫으면 FINISHED에 닿지 못해 기록이 사라진다
            case STARTED -> Set.of(FINISHED, CANCELLED);
            case FINISHED, CANCELLED -> Set.of();
        };
    }
}
