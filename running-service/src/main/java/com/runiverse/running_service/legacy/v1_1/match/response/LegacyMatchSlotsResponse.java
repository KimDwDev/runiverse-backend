package com.runiverse.running_service.legacy.v1_1.match.response;

import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult;

import java.time.LocalDateTime;
import java.util.List;

// 1.1.x 앱의 GET /running-matches/slots 응답 — 요청한 거리 하나의 슬롯만 { slots: [...] }로 내린다.
// 1.2.0은 거리별로 묶은 { distances: [...] }다(MatchSlotsResponse). 어느 거리를 고를지는 컨트롤러가 정한다
public record LegacyMatchSlotsResponse(List<MatchSlotResponse> slots) {

    public static LegacyMatchSlotsResponse from(GetMatchSlotsResult.DistanceSlots distanceSlots) {
        return new LegacyMatchSlotsResponse(distanceSlots.slots().stream()
                .map(MatchSlotResponse::from)
                .toList());
    }

    public record MatchSlotResponse(
            LocalDateTime scheduledStartAt,
            int waitingCount,
            boolean selectable
    ) {

        static MatchSlotResponse from(GetMatchSlotsResult.MatchSlot slot) {
            return new MatchSlotResponse(slot.scheduledStartAt(), slot.waitingCount(), slot.selectable());
        }
    }
}
