package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;

import java.util.Optional;

public interface LockRunningPlayerPort {

    // 취소와 시작이 같은 신청 행을 고친다 — 잠그지 않으면 낡은 스냅샷으로 deleted_at을 덮어써
    // 나간 사용자가 활성 신청으로 되살아난다. 읽는 순간 잠가야 상대 커밋을 보고 판단한다
    Optional<RunningPlayer> lockActive(UserId userId);
}
