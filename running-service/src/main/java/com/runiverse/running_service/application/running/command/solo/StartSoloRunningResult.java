package com.runiverse.running_service.application.running.command.solo;

import java.time.LocalDateTime;

public record StartSoloRunningResult(Long runningRoomId, LocalDateTime startAt) {

}
