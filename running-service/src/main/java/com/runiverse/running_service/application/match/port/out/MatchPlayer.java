package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;

import java.util.UUID;

public record MatchPlayer(
        UUID userId,
        RunningPlayerStatus status,
        int avgPaceSecondsPerKm
) {

}
