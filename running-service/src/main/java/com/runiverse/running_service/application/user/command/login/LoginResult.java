package com.runiverse.running_service.application.user.command.login;

import java.util.UUID;

public record LoginResult(
        UUID userId
) {
}
