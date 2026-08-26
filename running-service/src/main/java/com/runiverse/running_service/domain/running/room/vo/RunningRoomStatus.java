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

    private Set<RunningRoomStatus> allowedNext() {
        return switch (this) {
            case MATCHING -> Set.of(MATCHED, CANCELLED);
            case MATCHED -> Set.of(STARTED, CANCELLED);
            // 시작한 방은 유효 기록이 있으면 FINISHED, 하나도 없으면 CANCELLED로 닫힌다
            case STARTED -> Set.of(FINISHED, CANCELLED);
            case FINISHED, CANCELLED -> Set.of();
        };
    }
}
