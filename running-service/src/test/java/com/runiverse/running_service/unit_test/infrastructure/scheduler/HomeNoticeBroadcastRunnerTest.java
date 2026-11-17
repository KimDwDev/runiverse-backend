package com.runiverse.running_service.unit_test.infrastructure.scheduler;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.application.match.port.in.BroadcastWaitingCountUsecase;
import com.runiverse.running_service.application.running.port.in.BroadcastRunningActivityUsecase;
import com.runiverse.running_service.infrastructure.scheduler.HomeNoticeBroadcastRunner;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("홈 공지 전송 스케줄러 단위 테스트")
class HomeNoticeBroadcastRunnerTest {

    @Mock
    private BroadcastWaitingCountUsecase broadcastWaitingCountUsecase;

    @Mock
    private BroadcastRunningActivityUsecase broadcastRunningActivityUsecase;

    private HomeNoticeBroadcastRunner runner;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        runner = new HomeNoticeBroadcastRunner(broadcastWaitingCountUsecase, broadcastRunningActivityUsecase);
        log = LogCapture.of(HomeNoticeBroadcastRunner.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("대기 인원 주기에는 대기 인원만 보낸다")
    void broadcastsWaitingCountOnly() {
        // when
        runner.broadcastWaitingCount();

        // then
        verify(broadcastWaitingCountUsecase).broadcast();
        verifyNoInteractions(broadcastRunningActivityUsecase);
    }

    @Test
    @DisplayName("러닝 현황 주기에는 러닝 현황만 보낸다")
    void broadcastsRunningActivityOnly() {
        // when
        runner.broadcastRunningActivity();

        // then
        verify(broadcastRunningActivityUsecase).broadcast();
        verifyNoInteractions(broadcastWaitingCountUsecase);
    }

    @Test
    @DisplayName("대기 인원이 실패하면 [매칭] ERROR를 남기고 예외를 밖으로 내보내지 않는다")
    void logsWaitingCountFailure() {
        // given
        doThrow(new IllegalStateException("DB 오류")).when(broadcastWaitingCountUsecase).broadcast();

        // when & then -> 스케줄러 스레드로 예외가 새지 않는다
        assertThatCode(() -> runner.broadcastWaitingCount()).doesNotThrowAnyException();
        assertThat(log.messages(Level.ERROR)).containsExactly("[매칭] 대기 인원 조회 실패: 처리하지 못한 예외");
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }

    @Test
    @DisplayName("러닝 현황이 실패하면 [러닝] ERROR를 남기고 예외를 밖으로 내보내지 않는다")
    void logsActivityFailure() {
        // given
        doThrow(new IllegalStateException("DB 오류")).when(broadcastRunningActivityUsecase).broadcast();

        // when & then
        assertThatCode(() -> runner.broadcastRunningActivity()).doesNotThrowAnyException();
        assertThat(log.messages(Level.ERROR)).containsExactly("[러닝] 러닝 현황 조회 실패: 처리하지 못한 예외");
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }
}
