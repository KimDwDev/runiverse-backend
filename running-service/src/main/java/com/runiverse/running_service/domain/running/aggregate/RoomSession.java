package com.runiverse.running_service.domain.running.aggregate;

import com.runiverse.running_service.domain.running.vo.LeaveCount;
import com.runiverse.running_service.domain.running.vo.RunningPlayerId;
import lombok.Getter;

@Getter
public class RoomSession {

    private final RunningPlayerId runningPlayerId;
    private LeaveCount leaveCount;
    private boolean connected;

    RoomSession(Long runningPlayerId, int leaveCount, boolean connected) {
        this.runningPlayerId = new RunningPlayerId(runningPlayerId);
        this.leaveCount = new LeaveCount(leaveCount);
        this.connected = connected;
    }

    // 방 배정 시 — 처음 맺는 관계
    static RoomSession open(Long runningPlayerId) {
        return new RoomSession(runningPlayerId, 0, true);
    }

    // 방에서 나감 — 관계 row는 남기고 나간 이력만 새긴다
    void leave() {
        this.connected = false;
        this.leaveCount = leaveCount.increase();
    }

    // 다시 들어옴 — 세션을 새로 만들지 않고 되살린다
    void rejoin() {
        this.connected = true;
    }

    public boolean isSamePlayer(RunningPlayerId playerId) {
        return runningPlayerId.equals(playerId);
    }
}
