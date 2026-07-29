package com.runiverse.running_service.application.auth.command.reissue;

import java.util.UUID;

public record ReissueCommand(
        String refreshToken
) {
}
