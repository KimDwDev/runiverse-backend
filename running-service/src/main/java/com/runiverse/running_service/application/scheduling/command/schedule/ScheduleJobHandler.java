package com.runiverse.running_service.application.scheduling.command.schedule;

import com.runiverse.running_service.application.scheduling.port.in.ScheduleJobUsecase;
import com.runiverse.running_service.application.scheduling.port.out.SaveScheduledJobPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 예약을 대상 생성과 같은 트랜잭션에 남기고, 타이머·전파는 커밋 뒤로 미룬다
@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleJobHandler implements ScheduleJobUsecase {

    private final SaveScheduledJobPort saveScheduledJobPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void handle(ScheduleJobCommand command) {
        ScheduledJob job = saveScheduledJobPort.save(ScheduledJob.reserve(
                command.type(), command.targetId(), command.executeAt()));
        // 커밋 전에 타이머를 걸거나 전파하면 롤백된 예약을 깨우게 된다.
        // 방 생성이 실패하면 예약도 함께 사라져야 한다
        eventPublisher.publishEvent(new ScheduledJobCreatedEvent(job));
    }
}
