package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.player.RunningPlayer;

public interface DeleteRunningPlayerPort {

    // 이 신청이 들고 있던 방 세션도 함께 지운다 — 신청 없이 남을 행이 아니다
    void delete(RunningPlayer player);
}
