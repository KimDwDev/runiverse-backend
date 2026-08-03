package com.runiverse.running_service.presentation.user.response;

import java.util.UUID;

public record OnboardResponse(
        UUID userId,
        String nickname
) {
}
