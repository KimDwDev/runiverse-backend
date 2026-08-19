package com.runiverse.running_service.domain.running.vo;

import com.runiverse.running_service.domain.running.exception.InvalidRoomStatusTransitionException;

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
            case STARTED -> Set.of(FINISHED, CANCELLED);
            // 러닝이 시작 된 후에는 CANCELLED가 있을지 고민 중 현재는 없는 걸로 생각을 했습니다.
            case FINISHED, CANCELLED -> Set.of();
        };
    }
}
