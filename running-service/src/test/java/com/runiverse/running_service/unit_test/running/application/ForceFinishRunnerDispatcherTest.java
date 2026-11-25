package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishRunnerDispatcher;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("강제 종료 러너 워커 단위 테스트")
class ForceFinishRunnerDispatcherTest {

    private static final long ROOM_ID = 125L;
    private static final UserId FIRST = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId SECOND = new UserId(UuidCreator.getTimeOrderedEpoch());

    @Mock
    private FinishRunningUsecase finishRunningUsecase;

    private final List<Runnable> submitted = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("알람 트랜잭션이 커밋되기 전에는 워커에 넘기지 않는다")
    void waitsForCommit() {
        // given -> 다시 건 예약이 저장되기 전에 워커가 돌면, 도중에 죽었을 때 다음 알람이 없다
        TransactionSynchronizationManager.initSynchronization();
        ForceFinishRunnerDispatcher dispatcher = new ForceFinishRunnerDispatcher(submitted::add, finishRunningUsecase);

        // when
        dispatcher.dispatchAfterCommit(ROOM_ID, List.of(FIRST, SECOND));

        // then
        assertThat(submitted).isEmpty();

        // when -> 커밋
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // then -> 러너마다 따로 넘긴다
        assertThat(submitted).hasSize(2);
        verifyNoInteractions(finishRunningUsecase);
        submitted.forEach(Runnable::run);
        verify(finishRunningUsecase).handle(new FinishRunningCommand(ROOM_ID, FIRST.value(), true));
        verify(finishRunningUsecase).handle(new FinishRunningCommand(ROOM_ID, SECOND.value(), true));
    }

    @Test
    @DisplayName("트랜잭션 밖이면 바로 넘긴다")
    void dispatchesImmediatelyWithoutTransaction() {
        // given -> 리스너 경로 등
        ForceFinishRunnerDispatcher dispatcher = new ForceFinishRunnerDispatcher(submitted::add, finishRunningUsecase);

        // when
        dispatcher.dispatchAfterCommit(ROOM_ID, List.of(FIRST));

        // then
        assertThat(submitted).hasSize(1);
    }
}
