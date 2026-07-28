package com.runiverse.running_service.application.user.command.logout;

import java.util.UUID;

public record LogoutResult(
        UUID userID
) {
}
