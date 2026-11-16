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
    @DisplayName("실행되면 대기 인원과 러닝 현황을 둘 다 보낸다")
    void broadcastsBoth() {
        // when
        runner.broadcastOnSchedule();

        // then
        verify(broadcastWaitingCountUsecase).broadcast();
        verify(broadcastRunningActivityUsecase).broadcast();
    }

    @Test
    @DisplayName("대기 인원이 실패해도 러닝 현황은 보내고 [매칭] ERROR를 남긴다")
    void keepsActivityWhenWaitingFails() {
        // given
        doThrow(new IllegalStateException("DB 오류")).when(broadcastWaitingCountUsecase).broadcast();

        // when & then -> 스케줄러 스레드로 예외가 새지 않는다
        assertThatCode(() -> runner.broadcastOnSchedule()).doesNotThrowAnyException();
        verify(broadcastRunningActivityUsecase).broadcast();
        assertThat(log.messages(Level.ERROR)).containsExactly("[매칭] 대기 인원 조회 실패: 처리하지 못한 예외");
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }

    @Test
    @DisplayName("러닝 현황이 실패하면 [러닝] ERROR를 남기고 예외를 밖으로 내보내지 않는다")
    void logsActivityFailure() {
        // given
        doThrow(new IllegalStateException("DB 오류")).when(broadcastRunningActivityUsecase).broadcast();

        // when & then
        assertThatCode(() -> runner.broadcastOnSchedule()).doesNotThrowAnyException();
        verify(broadcastWaitingCountUsecase).broadcast();
        assertThat(log.messages(Level.ERROR)).containsExactly("[러닝] 러닝 현황 조회 실패: 처리하지 못한 예외");
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }
}
