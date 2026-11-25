package com.runiverse.running_service.application.running.command.start;

import com.runiverse.running_service.application.running.port.in.StartRunningRoomUsecase;
import com.runiverse.running_service.application.scheduling.ScheduledJobExecutor;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RunningStartExecutor implements ScheduledJobExecutor {

    private final StartRunningRoomUsecase startRunningRoomUsecase;

    @Override
    public ScheduledJobType type() {
        return ScheduledJobType.RUNNING_START;
    }

    @Override
    public Optional<LocalDateTime> execute(JobTarget target) {
        startRunningRoomUsecase.handle(new StartRunningRoomCommand(target.idAsLong()));
        return Optional.empty();
    }
}
