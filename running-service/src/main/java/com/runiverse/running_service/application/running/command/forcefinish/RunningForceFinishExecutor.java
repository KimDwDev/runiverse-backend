package com.runiverse.running_service.application.running.command.forcefinish;

import com.runiverse.running_service.application.running.port.in.ForceFinishRunningRoomUsecase;
import com.runiverse.running_service.application.scheduling.ScheduledJobExecutor;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RunningForceFinishExecutor implements ScheduledJobExecutor {

    private final ForceFinishRunningRoomUsecase forceFinishRunningRoomUsecase;

    @Override
    public ScheduledJobType type() {
        return ScheduledJobType.RUNNING_FORCE_FINISH;
    }

    @Override
    public Optional<LocalDateTime> execute(JobTarget target) {
        forceFinishRunningRoomUsecase.handle(
                new ForceFinishRunningRoomCommand(target.idAsLong()));
        return Optional.empty();
    }
}
