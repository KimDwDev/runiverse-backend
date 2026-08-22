package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;

import java.util.Optional;

public interface LoadActiveRunningPlayerPort {

    // 활성 신청은 유저당 하나다 — 커맨드에 없는 runningPlayerId를 여기서 얻는다
    Optional<RunningPlayer> loadActive(UserId userId);
}
