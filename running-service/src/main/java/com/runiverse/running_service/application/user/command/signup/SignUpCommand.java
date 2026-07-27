package com.runiverse.running_service.application.user.command.signup;

import java.util.UUID;

public record SignUpCommand(
        String email,
        String password
) {
}
