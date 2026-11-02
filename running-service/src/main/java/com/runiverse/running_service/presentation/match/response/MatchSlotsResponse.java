package com.runiverse.running_service.presentation.match.response;

import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult;

import java.time.LocalDateTime;
import java.util.List;

public record MatchSlotsResponse(List<DistanceSlotsResponse> distances) {

    public static MatchSlotsResponse from(GetMatchSlotsResult result) {
        return new MatchSlotsResponse(result.distances().stream()
                .map(DistanceSlotsResponse::from)
                .toList());
    }

    public record DistanceSlotsResponse(
            int targetDistanceMeters,
            List<MatchSlotResponse> slots
    ) {

        static DistanceSlotsResponse from(GetMatchSlotsResult.DistanceSlots distanceSlots) {
            return new DistanceSlotsResponse(distanceSlots.targetDistanceMeters(), distanceSlots.slots().stream()
                    .map(MatchSlotResponse::from)
                    .toList());
        }
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
