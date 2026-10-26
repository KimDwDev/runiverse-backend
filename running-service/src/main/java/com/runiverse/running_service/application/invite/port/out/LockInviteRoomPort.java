package com.runiverse.running_service.application.invite.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.Optional;

public interface LockInviteRoomPort {

    // 초대는 방의 자식이라 방 행 잠금 하나로 그 방의 초대가 모두 잠긴다 —
    // 수락 순서(먼저 수락한 사람이 자리를 잡는다)도 이 잠금이 정한다.
    // 세션과 초대를 함께 복원해야 방이 참가 여부·받는 사람 상태를 판정할 수 있다
    Optional<RunningRoom> lockById(RunningRoomId runningRoomId);
}
