package com.runiverse.running_service.application.user.command.nickname;

import java.util.UUID;

public record ChangeNicknameResult(
        UUID userId,
        String nickname
) {

}
