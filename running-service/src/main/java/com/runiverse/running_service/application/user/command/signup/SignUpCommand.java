package com.runiverse.running_service.application.user.command.signup;

import java.util.UUID;

public record SignUpCommand(
        UUID userId,
        String email,
        String password
) {
}
