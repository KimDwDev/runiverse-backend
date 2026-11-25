package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishRunningRoomCommand;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ForceFinishRunningRoomUsecase {

    // 뛰던 사람이 남아 다시 봐야 하면 그 시각을, 다 끝났으면 empty를 돌려준다(D-83)
    Optional<LocalDateTime> handle(ForceFinishRunningRoomCommand command);
}
