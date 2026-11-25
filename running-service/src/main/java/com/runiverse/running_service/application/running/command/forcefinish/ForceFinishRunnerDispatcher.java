package com.runiverse.running_service.application.running.command.forcefinish;

import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.exception.RunningFinishInProgressException;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

// 강제 종료의 워커 — 뛰던 사람을 한 명씩 일반 종료(forced=true)와 같은 길로 끝낸다(D-83).
// 다 끝났는지는 워커가 아니라 다음 알람이 DB로 판단한다 — 워커는 누구에게도 결과를 알리지 않는다
@Slf4j
@Component
public class ForceFinishRunnerDispatcher {

    private final TaskExecutor forceFinishExecutor;
    private final FinishRunningUsecase finishRunningUsecase;

    public ForceFinishRunnerDispatcher(@Qualifier("forceFinishExecutor") TaskExecutor forceFinishExecutor,
                                       FinishRunningUsecase finishRunningUsecase) {
        this.forceFinishExecutor = forceFinishExecutor;
        this.finishRunningUsecase = finishRunningUsecase;
    }

    // 알람 트랜잭션이 커밋된 뒤에 넘긴다 — 다시 건 예약이 저장된 뒤라 워커가 죽어도 다음 알람이 남는다.
    // 커밋 콜백에서 바로 끝내면 끝난 트랜잭션에 합류해 저장되지 않으므로 다른 스레드에서 연다
    public void dispatchAfterCommit(Long runningRoomId, List<UserId> runners) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(runningRoomId, runners);
                }
            });
            return;
        }
        dispatch(runningRoomId, runners);
    }

    private void dispatch(Long runningRoomId, List<UserId> runners) {
        for (UserId runner : runners) {
            forceFinishExecutor.execute(() -> finish(runningRoomId, runner));
        }
    }

    // 실패해도 던지지 않는다 — 워커 스레드로 올라가면 아무도 못 본다. 남은 러너는 다음 알람이 다시 넘긴다
    private void finish(Long runningRoomId, UserId runner) {
        try {
            finishRunningUsecase.handle(new FinishRunningCommand(runningRoomId, runner.value(), true));
        } catch (RunningFinishInProgressException e) {
            // 본인 종료나 앞 알람의 워커가 같은 러너를 끝내는 중이다
            log.info("[러닝] 강제 종료 러너 건너뜀: 다른 종료가 처리 중 - roomId={}, userId={}",
                    runningRoomId, runner.value());
        } catch (RuntimeException e) {
            log.warn("[러닝] 강제 종료 러너 처리 실패: 다음 알람에서 다시 - roomId={}, userId={}",
                    runningRoomId, runner.value(), e);
        }
    }
}
