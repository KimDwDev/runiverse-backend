package com.runiverse.running_service.unit_test.legacy.v1_1.match;

import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult;
import com.runiverse.running_service.legacy.v1_1.match.response.LegacyMatchSlotsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("1.1.x 슬롯 조회 응답 변환 단위 테스트")
class LegacyMatchSlotsResponseTest {

    private static final LocalDateTime SEVEN = LocalDateTime.of(2026, 11, 3, 7, 0);
    private static final LocalDateTime EIGHT = LocalDateTime.of(2026, 11, 3, 8, 0);

    @Test
    @DisplayName("거리 하나의 슬롯을 순서·값 그대로 slots로 옮긴다")
    void flattensOneDistance() {
        // given
        GetMatchSlotsResult.DistanceSlots distanceSlots = new GetMatchSlotsResult.DistanceSlots(5000, List.of(
                new GetMatchSlotsResult.MatchSlot(SEVEN, 2, false),
                new GetMatchSlotsResult.MatchSlot(EIGHT, 0, true)));

        // when
        LegacyMatchSlotsResponse response = LegacyMatchSlotsResponse.from(distanceSlots);

        // then -> 1.1.x 앱은 거리 묶음 없이 slots 배열만 읽는다
        assertThat(response.slots()).containsExactly(
                new LegacyMatchSlotsResponse.MatchSlotResponse(SEVEN, 2, false),
                new LegacyMatchSlotsResponse.MatchSlotResponse(EIGHT, 0, true));
    }

    @Test
    @DisplayName("슬롯이 없으면 빈 배열이다")
    void emptySlotsStayEmpty() {
        // given
        GetMatchSlotsResult.DistanceSlots distanceSlots = new GetMatchSlotsResult.DistanceSlots(5000, List.of());

        // when & then -> null이 아니라 빈 배열로 내려야 앱이 목록을 그린다
        assertThat(LegacyMatchSlotsResponse.from(distanceSlots).slots()).isEmpty();
    }

    @Test
    @DisplayName("응답 필드는 운영(1.1.1 서버)과 같은 slots 하나다")
    void keepsWireShape() {
        // when & then -> 이름이 바뀌면 1.1.x 앱이 목록을 못 읽는다
        assertThat(LegacyMatchSlotsResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("slots");
        assertThat(LegacyMatchSlotsResponse.MatchSlotResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("scheduledStartAt", "waitingCount", "selectable");
    }
}
