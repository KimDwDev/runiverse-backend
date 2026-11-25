package com.runiverse.running_service.application.scheduling.command.run;

import com.runiverse.running_service.application.scheduling.ScheduledJobExecutor;
import com.runiverse.running_service.application.scheduling.command.schedule.ScheduledJobRescheduledEvent;
import com.runiverse.running_service.application.scheduling.port.in.RunScheduledJobUsecase;
import com.runiverse.running_service.application.scheduling.port.out.LockScheduledJobPort;
import com.runiverse.running_service.application.scheduling.port.out.UpdateScheduledJobPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// 예약을 선점하고 타입별 실행기로 넘긴다.
// 선점과 실행이 한 트랜잭션이다 — 실행이 터지면 is_sent도 함께 롤백돼
// 잠금을 기다리던 인스턴스가 정상적으로 다시 시도한다.
// 실행기가 끝나지 않았다고 답하면 같은 예약을 그 시각으로 다시 건다(강제 종료, D-83)
@Slf4j
@Service
@Transactional
public class RunScheduledJobHandler implements RunScheduledJobUsecase {

    private final LockScheduledJobPort lockScheduledJobPort;
    private final UpdateScheduledJobPort updateScheduledJobPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<ScheduledJobType, ScheduledJobExecutor> executors;

    public RunScheduledJobHandler(LockScheduledJobPort lockScheduledJobPort,
                                  UpdateScheduledJobPort updateScheduledJobPort,
                                  ApplicationEventPublisher eventPublisher,
                                  List<ScheduledJobExecutor> executors) {
        this.lockScheduledJobPort = lockScheduledJobPort;
        this.updateScheduledJobPort = updateScheduledJobPort;
        this.eventPublisher = eventPublisher;
        this.executors = executors.stream()
                .collect(Collectors.toMap(ScheduledJobExecutor::type, Function.identity()));
    }

    @Override
    public void handle(RunScheduledJobCommand command) {
        // 1. 여러 인스턴스가 같은 예약을 타이머로 들고 있다 — 잠그고 다시 읽어야 하나만 통과한다.
        // 잠그지 않으면 둘 다 sent=false를 보고 함께 지나간다
        Optional<ScheduledJob> locked =
                lockScheduledJobPort.lockById(new ScheduledJobId(command.scheduledJobId()));
        if (locked.isEmpty()) {
            log.warn("[예약] 예약 실행 건너뜀: 예약 없음 - scheduledJobId={}", command.scheduledJobId());
            return;
        }
        ScheduledJob job = locked.get();
        // 2. 남이 먼저 이겼다. markSent()가 던지기 전에 조용히 빠진다
        if (job.isSent()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        // 3. 다시 건 예약을 옛 시각의 타이머가 깨웠다 — 잠금을 기다리던 다른 인스턴스가 앞 실행이
        //    다시 걸어 둔 행을 보는 경우다. 지금 실행하면 1분 간격이 무너진다. 새 시각의 타이머가 다시 깨운다
        if (!job.isDue(now)) {
            return;
        }
        job.markSent(now);
        JobTarget target = job.getTarget();
        Optional<LocalDateTime> retryAt = executorOf(target.type()).execute(target);
        // 4. 끝나지 않았다 — 완료로 적지 않고 같은 행을 다시 건다. 실행 전에 걸어 두는 것과 같다:
        //    커밋되면 이 실행의 뒷일(워커)이 도중에 죽어도 그 시각에 누군가 다시 깬다
        retryAt.ifPresent(job::reschedule);
        updateScheduledJobPort.update(job);
        retryAt.ifPresent(at -> eventPublisher.publishEvent(new ScheduledJobRescheduledEvent(job)));
    }

    private ScheduledJobExecutor executorOf(ScheduledJobType type) {
        ScheduledJobExecutor executor = executors.get(type);
        if (executor == null) {
            // 타입을 추가하고 실행기를 빠뜨린 경우다 — 컴파일러가 못 잡는 자리라 여기서 세운다
            throw new IllegalStateException("예약 타입의 실행기가 없다 — type=" + type);
        }
        return executor;
    }
}
