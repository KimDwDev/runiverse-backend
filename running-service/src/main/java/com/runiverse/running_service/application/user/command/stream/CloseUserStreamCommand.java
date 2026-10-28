package com.runiverse.running_service.application.user.command.stream;

import com.runiverse.running_service.application.user.port.out.UserStreamConnection;

import java.util.UUID;

public record CloseUserStreamCommand(UUID userId, UserStreamConnection connection) {

}
