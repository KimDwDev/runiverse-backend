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
        // 뛰던 사람이 남았으면 그 시각으로 다시 건다 — 다 끝난 것을 본 알람만 완료로 적는다(D-83)
        return forceFinishRunningRoomUsecase.handle(
                new ForceFinishRunningRoomCommand(target.idAsLong()));
    }
}
