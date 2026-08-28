package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationCommand;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationHandler;
import com.runiverse.running_service.application.running.port.out.AppendRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 위치 업데이트 단위 테스트")
public class UpdateRunningLocationHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long ROOM_ID = 125L;

    @Mock
    private AppendRunningTrackPort appendRunningTrackPort;

    @InjectMocks
    private UpdateRunningLocationHandler updateRunningLocationHandler;

    private static TrackPoint trackPoint(long sequence) {
        return new TrackPoint(
                sequence,
                37.5665,
                126.9780,
                38.5,
                4.2,
                2.8,
                181.0,
                174,
                357,
                LocalDateTime.of(2026, 8, 25, 7, 30, (int) sequence));
    }

    @Test
    @DisplayName("받은 좌표를 순서 그대로 트랙에 넘긴다")
    void appendsPointsInOrder() {
        // given
        List<TrackPoint> points = List.of(trackPoint(1L), trackPoint(2L), trackPoint(3L));

        // when
        updateRunningLocationHandler.handle(
                new UpdateRunningLocationCommand(USER_ID, ROOM_ID, points));

        // then
        verify(appendRunningTrackPort).append(ROOM_ID, new UserId(USER_ID), points);
    }

    @Test
    @DisplayName("좌표가 비어 있어도 트랙 적재를 호출한다")
    void appendsEvenWhenPointsAreEmpty() {
        // given -> 빈 배치를 걸러내는 책임은 핸들러가 아니라 요청 검증에 있다
        // when
        updateRunningLocationHandler.handle(
                new UpdateRunningLocationCommand(USER_ID, ROOM_ID, List.of()));

        // then
        verify(appendRunningTrackPort).append(eq(ROOM_ID), eq(new UserId(USER_ID)), anyList());
    }
}
