package com.runiverse.running_service.application.invite.command.open;

import java.time.LocalDateTime;
import java.util.UUID;

public record OpenInviteRoomCommand(UUID userId, LocalDateTime scheduledStartAt, int targetDistanceMeters) {

}
