package com.runiverse.running_service.domain.running.room;

import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;

public record SessionDraft(RunningPlayerId runningPlayerId, int leaveCount, boolean connected) {

}
