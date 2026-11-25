package com.runiverse.running_service.application.match.command.ready;

import com.runiverse.running_service.application.match.port.in.NotifyRunningReadyUsecase;
import com.runiverse.running_service.application.scheduling.ScheduledJobExecutor;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RunningReadyExecutor implements ScheduledJobExecutor {

    private final NotifyRunningReadyUsecase notifyRunningReadyUsecase;

    @Override
    public ScheduledJobType type() {
        return ScheduledJobType.RUNNING_READY;
    }

    @Override
    public Optional<LocalDateTime> execute(JobTarget target) {
        notifyRunningReadyUsecase.handle(new NotifyRunningReadyCommand(target.idAsLong()));
        return Optional.empty();
    }
}
