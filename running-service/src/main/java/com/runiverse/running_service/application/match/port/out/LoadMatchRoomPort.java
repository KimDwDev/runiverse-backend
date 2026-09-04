package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.Optional;

public interface LoadMatchRoomPort {

    // running_players엔 방 ID가 없다 — 현재 배정된 방은 running_room_sessions의
    // is_connected=true인 행이 갖는다(erd: 방 이동을 담을 수 있게 컬럼으로 두지 않았다)
    Optional<RunningRoomId> findAssignedRoom(RunningPlayerId runningPlayerId);
}
