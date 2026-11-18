package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.running.command.activity.RunningActivity;
import com.runiverse.running_service.application.running.command.activity.RunningActivityReader;
import com.runiverse.running_service.application.running.port.out.FinishedRunRow;
import com.runiverse.running_service.application.running.port.out.LoadRunningActivityPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("홈 러닝 현황 읽기 단위 테스트")
class RunningActivityReaderTest {

    private static final Duration FORCE_FINISH_OFFSET = Duration.ofHours(6);

    @Mock
    private LoadRunningActivityPort loadRunningActivityPort;

    private RunningActivityReader reader;

    @BeforeEach
    void setUp() {
        MatchProperties properties = new MatchProperties(
                Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofSeconds(10), FORCE_FINISH_OFFSET,
                10, Duration.ofMinutes(20),
                LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000));
        reader = new RunningActivityReader(loadRunningActivityPort, properties);
    }

    @Test
    @DisplayName("강제 종료 시간이 지난 러닝은 달리는 중으로 세지 않는다")
    void countsRunningStartedWithinForceFinishOffset() {
        // given -> 서버가 닫지 않는 솔로는 앱이 죽으면 RUNNING이 남는다
        given(loadRunningActivityPort.countRunningPlayers(any())).willReturn(3);
        LocalDateTime before = LocalDateTime.now();

        // when
        RunningActivity activity = reader.read();

        // then
        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<LocalDateTime> startedAfter = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(loadRunningActivityPort).countRunningPlayers(startedAfter.capture());
        assertThat(startedAfter.getValue())
                .isBetween(before.minus(FORCE_FINISH_OFFSET), after.minus(FORCE_FINISH_OFFSET));
        assertThat(activity.runningNowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("오늘 완주는 오늘 0시부터, 어제 완주는 어제 0시부터 오늘 0시 전까지 센다")
    void splitsDaysAtMidnight() {
        // given
        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        given(loadRunningActivityPort.countFinishers(today, today.plusDays(1))).willReturn(7);
        given(loadRunningActivityPort.countFinishers(today.minusDays(1), today)).willReturn(5);

        // when
        RunningActivity activity = reader.read();

        // then
        verify(loadRunningActivityPort).loadLatestFinishedRuns(today, today.plusDays(1), 3);
        assertThat(activity.todayFinishedCount()).isEqualTo(7);
        assertThat(activity.yesterdayFinishedCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("완주 소식의 닉네임은 길이와 관계없이 첫 글자 뒤를 **로 가린다")
    void masksNicknameWithFixedLength() {
        // given -> 두 글자도 한 글자로 줄이지 않고 같은 길이로 가린다
        given(loadRunningActivityPort.loadLatestFinishedRuns(any(), any(), anyInt())).willReturn(List.of(
                new FinishedRunRow(102L, "철수", 5_000),
                new FinishedRunRow(101L, "runner_kim", 3_210)));

        // when
        RunningActivity activity = reader.read();

        // then
        assertThat(activity.todayFinishedRuns()).containsExactly(
                new RunningActivity.FinishedRun(102L, "철**", 5_000),
                new RunningActivity.FinishedRun(101L, "r**", 3_210));
    }

    @Test
    @DisplayName("읽기 트랜잭션을 리더가 연다")
    void opensReadOnlyTransaction() throws NoSuchMethodException {
        // when & then -> 전송 핸들러는 트랜잭션을 열지 않으므로 여기서 연다
        Transactional transactional = RunningActivityReader.class.getMethod("read").getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
