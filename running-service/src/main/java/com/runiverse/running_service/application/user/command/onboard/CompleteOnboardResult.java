package com.runiverse.running_service.application.user.command.onboard;

import java.util.UUID;

public record CompleteOnboardResult(UUID userId, String nickname) {}
