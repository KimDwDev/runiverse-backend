package com.runiverse.running_service.domain.running.room;

import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.vo.LeaveCount;
import lombok.Getter;

@Getter
public class RoomSession {

    private final RunningPlayerId runningPlayerId;
    private LeaveCount leaveCount;
    private boolean connected;

    // private으로 설정함으로써 외부에서는 생성이 불가능 하다
    private RoomSession(RunningPlayerId runningPlayerId, int leaveCount, boolean connected) {
        this.runningPlayerId = runningPlayerId;
        this.leaveCount = new LeaveCount(leaveCount);
        this.connected = connected;
    }

    // default 패키지로 내림으로써 같은 패키지인 room에서만 접근이 가능하다
    // 방 배정 시 — 처음 맺는 관계
    static RoomSession open(RunningPlayerId runningPlayerId) {
        return new RoomSession(runningPlayerId, 0, true);
    }

    // DB 복원 — 방을 거쳐야만 들어온다
    static RoomSession from(SessionDraft draft) {
        return new RoomSession(draft.runningPlayerId(),
                draft.leaveCount(), draft.connected());
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
