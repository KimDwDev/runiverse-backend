package com.runiverse.running_service.unit_test.scheduling.application;

import com.runiverse.running_service.application.scheduling.ScheduledJobExecutor;
import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobCommand;
import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobHandler;
import com.runiverse.running_service.application.scheduling.command.schedule.ScheduledJobRescheduledEvent;
import com.runiverse.running_service.application.scheduling.port.out.LockScheduledJobPort;
import com.runiverse.running_service.application.scheduling.port.out.UpdateScheduledJobPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("예약 실행 단위 테스트")
class RunScheduledJobHandlerTest {

    private static final long JOB_ID = 3L;
    private static final long ROOM_ID = 125L;
    private static final LocalDateTime EXECUTE_AT = LocalDateTime.of(2026, 9, 6, 19, 50);

    @Mock
    private LockScheduledJobPort lockScheduledJobPort;

    @Mock
    private UpdateScheduledJobPort updateScheduledJobPort;

    private RecordingExecutor matchCloseExecutor;
    private List<Object> events;
    private RunScheduledJobHandler runScheduledJobHandler;

    @BeforeEach
    void setUp() {
        matchCloseExecutor = new RecordingExecutor(ScheduledJobType.MATCH_CLOSE);
        events = new ArrayList<>();
        runScheduledJobHandler = new RunScheduledJobHandler(
                lockScheduledJobPort, updateScheduledJobPort, events::add, List.of(matchCloseExecutor));
    }

    @Test
    @DisplayName("선점하면 타입에 맞는 실행기로 넘긴다")
    void runsExecutorAfterClaim() {
        // given -> 타이머가 마감 시각에 깨워서 부른다
        givenLocked(pendingJob());

        // when
        runScheduledJobHandler.handle(new RunScheduledJobCommand(JOB_ID));

        // then -> 대상은 문자열로 담겨 있고 실행기가 자기 타입에 맞게 되돌린다
        assertThat(matchCloseExecutor.executed)
                .containsExactly(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID));
    }

    @Test
    @DisplayName("선점하면 발화로 굳히고 저장한다")
    void marksAndPersistsSent() {
        // given
        givenLocked(pendingJob());

        // when
        runScheduledJobHandler.handle(new RunScheduledJobCommand(JOB_ID));

        // then -> 선점과 실행이 한 트랜잭션이라 실행이 터지면 이 값도 함께 롤백된다
        ArgumentCaptor<ScheduledJob> captor = ArgumentCaptor.forClass(ScheduledJob.class);
        verify(updateScheduledJobPort).update(captor.capture());
        assertThat(captor.getValue().isSent()).isTrue();
        assertThat(captor.getValue().getSentAt()).isPresent();
    }

    @Test
    @DisplayName("남이 이미 발화했으면 조용히 빠진다")
    void skipsWhenAlreadySent() {
        // given -> 인스턴스 셋이 같은 예약을 들고 있으면 둘은 지는 게 정상 경로다.
        //          도메인은 두 번째 발화에 던지지만 여기서는 예외로 흐름을 만들지 않는다
        givenLocked(sentJob());

        // when
        runScheduledJobHandler.handle(new RunScheduledJobCommand(JOB_ID));

        // then
        assertThat(matchCloseExecutor.executed).isEmpty();
        verifyNoInteractions(updateScheduledJobPort);
    }

    @Test
    @DisplayName("예약이 사라졌으면 아무것도 하지 않는다")
    void skipsWhenJobIsGone() {
        // given
        given(lockScheduledJobPort.lockById(new ScheduledJobId(JOB_ID)))
                .willReturn(Optional.empty());

        // when
        runScheduledJobHandler.handle(new RunScheduledJobCommand(JOB_ID));

        // then
        assertThat(matchCloseExecutor.executed).isEmpty();
        verifyNoInteractions(updateScheduledJobPort);
    }

    @Test
    @DisplayName("실행기가 없는 타입이면 세운다")
    void failsWhenExecutorIsMissing() {
        // given -> 타입을 추가하고 실행기를 빠뜨린 경우. 컴파일러가 못 잡는 자리다
        RunScheduledJobHandler handlerWithoutExecutor = new RunScheduledJobHandler(
                lockScheduledJobPort, updateScheduledJobPort, events::add, List.of());
        givenLocked(pendingJob());

        // when & then -> 조용히 넘기면 그 예약은 발화만 되고 아무 일도 일어나지 않는다
        assertThatThrownBy(() ->
                handlerWithoutExecutor.handle(new RunScheduledJobCommand(JOB_ID)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("실행기가 끝나지 않았다고 답하면 완료로 적지 않고 같은 예약을 그 시각으로 다시 건다")
    void reschedulesUnfinishedJob() {
        // given -> 강제 종료에서 뛰는 사람이 남았다
        LocalDateTime retryAt = LocalDateTime.now().plusMinutes(1);
        matchCloseExecutor.retryAt = Optional.of(retryAt);
        givenLocked(pendingJob());

        // when
        runScheduledJobHandler.handle(new RunScheduledJobCommand(JOB_ID));

        // then -> 실행 전에 걸어 둔 것과 같다 — 뒷일이 도중에 죽어도 그 시각에 누군가 다시 깬다
        ArgumentCaptor<ScheduledJob> captor = ArgumentCaptor.forClass(ScheduledJob.class);
        verify(updateScheduledJobPort).update(captor.capture());
        assertThat(captor.getValue().isSent()).isFalse();
        assertThat(captor.getValue().getSentAt()).isEmpty();
        assertThat(captor.getValue().getExecuteAt()).isEqualTo(retryAt);
        // 커밋 뒤 타이머를 다시 건다 — 이 인스턴스의 옛 타이머는 발화 때 지워졌다
        assertThat(events).singleElement().isInstanceOf(ScheduledJobRescheduledEvent.class);
    }

    @Test
    @DisplayName("끝났으면 다시 걸지 않고 타이머 이벤트도 내지 않는다")
    void doesNotRescheduleFinishedJob() {
        // given
        givenLocked(pendingJob());

        // when
        runScheduledJobHandler.handle(new RunScheduledJobCommand(JOB_ID));

        // then
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("다시 건 시각이 아직이면 옛 타이머가 깨워도 실행하지 않는다")
    void skipsJobNotYetDue() {
        // given -> 잠금을 기다리던 다른 인스턴스가, 앞 실행이 1분 뒤로 다시 걸어 둔 행을 본다
        givenLocked(ScheduledJob.builder()
                .scheduledJobId(JOB_ID)
                .target(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID))
                .executeAt(LocalDateTime.now().plusMinutes(1))
                .build());

        // when
        runScheduledJobHandler.handle(new RunScheduledJobCommand(JOB_ID));

        // then -> 지금 실행하면 1분 간격이 무너진다. 새 시각의 타이머가 다시 깨운다
        assertThat(matchCloseExecutor.executed).isEmpty();
        verifyNoInteractions(updateScheduledJobPort);
    }

    private void givenLocked(ScheduledJob job) {
        given(lockScheduledJobPort.lockById(new ScheduledJobId(JOB_ID)))
                .willReturn(Optional.of(job));
    }

    private static ScheduledJob pendingJob() {
        return ScheduledJob.builder()
                .scheduledJobId(JOB_ID)
                .target(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID))
                .executeAt(EXECUTE_AT)
                .build();
    }

    private static ScheduledJob sentJob() {
        return ScheduledJob.builder()
                .scheduledJobId(JOB_ID)
                .target(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID))
                .executeAt(EXECUTE_AT)
                .sent(true)
                .sentAt(EXECUTE_AT)
                .build();
    }

    // 실행기는 스프링이 List로 주입하므로 목보다 실물이 실제 배선에 가깝다
    private static final class RecordingExecutor implements ScheduledJobExecutor {

        private final ScheduledJobType type;
        private final List<JobTarget> executed = new ArrayList<>();
        private Optional<LocalDateTime> retryAt = Optional.empty();

        private RecordingExecutor(ScheduledJobType type) {
            this.type = type;
        }

        @Override
        public ScheduledJobType type() {
            return type;
        }

        @Override
        public Optional<LocalDateTime> execute(JobTarget target) {
            executed.add(target);
            return retryAt;
        }
    }
}
