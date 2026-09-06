package com.runiverse.running_service.application.match.command.broadcast;

import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;

import java.util.List;
import java.util.UUID;

public record BroadcastMatchEventCommand(List<UUID> userIds, MatchStreamEvent event) {

}
