package com.runiverse.running_service.unit_test.match.application;

import com.runiverse.running_service.application.match.command.waiting.WaitingCountReader;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchSlotPolicy;
import com.runiverse.running_service.application.match.port.out.CountJoinableWaitingPlayersPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

// 칸은 실제 정책으로 거른다 — 신청 화면 슬롯과 같은 칸만 세는지가 이 계산의 요점이다.
// 리더가 지금 시각으로 판정하므로 칸을 지금 시각에서 만든다 — 하루 종일 1분 간격 칸에 마감 오프셋 0이라
// CLOSED는 지금 이하라 늘 마감, OPEN은 한 시간 뒤라 늘 열림이다. 리더는 포트가 준 칸의 날짜를 보지 않는다
@ExtendWith(MockitoExtension.class)
@DisplayName("오늘 대기 인원 합계 단위 테스트")
class WaitingCountReaderTest {

    private static final LocalDateTime CLOSED = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    private static final LocalDateTime OPEN = CLOSED.plusHours(1);

    @Mock
    private CountJoinableWaitingPlayersPort countJoinableWaitingPlayersPort;

    private WaitingCountReader reader;

    @BeforeEach
    void setUp() {
        MatchProperties properties = new MatchProperties(
                Duration.ZERO, Duration.ofMinutes(5), Duration.ofSeconds(10), Duration.ofHours(6),
                10, Duration.ofMinutes(20),
                LocalTime.of(0, 0), LocalTime.of(23, 59), Duration.ofMinutes(1), List.of(3_000, 5_000));
        reader = new WaitingCountReader(countJoinableWaitingPlayersPort, new MatchSlotPolicy(properties));
    }

    @Test
    @DisplayName("마감 전인 칸의 인원을 거리와 시각을 가리지 않고 더한다")
    void sumsOpenSlotsAcrossDistances() {
        // given
        given(countJoinableWaitingPlayersPort.countJoinableWaitingPlayers(any())).willReturn(Map.of(
                3_000, Map.of(OPEN, 2, OPEN.plusMinutes(1), 1),
                5_000, Map.of(OPEN, 3)));

        // when
        int waitingCount = reader.read();

        // then
        assertThat(waitingCount).isEqualTo(6);
    }

    @Test
    @DisplayName("마감한 칸, 선택지 밖 거리·시각의 방은 세지 않는다")
    void skipsSlotsTheApplyScreenDoesNotShow() {
        // given -> OPEN 3,000m 칸 하나만 신청 화면에 있는 칸이다
        given(countJoinableWaitingPlayersPort.countJoinableWaitingPlayers(any())).willReturn(Map.of(
                3_000, Map.of(
                        CLOSED, 4,                     // 마감
                        OPEN, 1,
                        OPEN.plusSeconds(30), 5),      // 간격 밖 시각
                7_000, Map.of(OPEN, 6)));              // 선택지 밖 거리

        // when
        int waitingCount = reader.read();

        // then
        assertThat(waitingCount).isEqualTo(1);
    }

    @Test
    @DisplayName("칸이 하나도 없으면 0이다")
    void returnsZeroWithoutSlots() {
        // given
        given(countJoinableWaitingPlayersPort.countJoinableWaitingPlayers(any())).willReturn(Map.of());

        // when & then
        assertThat(reader.read()).isZero();
    }

    @Test
    @DisplayName("읽기 트랜잭션을 리더가 연다")
    void opensReadOnlyTransaction() throws NoSuchMethodException {
        // when & then -> 전송 핸들러는 트랜잭션을 열지 않으므로 여기서 연다
        Transactional transactional = WaitingCountReader.class.getMethod("read").getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
