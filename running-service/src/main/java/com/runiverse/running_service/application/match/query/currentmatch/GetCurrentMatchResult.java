package com.runiverse.running_service.application.match.query.currentmatch;

import com.runiverse.running_service.application.match.query.roominfo.RoomInfo;

public record GetCurrentMatchResult(
        MatchState state,
        // 아래 둘은 NONE이면 null이다(api-spec 5-A)
        Long runningRoomId,
        RoomInfo room
) {

    public static GetCurrentMatchResult none() {
        return new GetCurrentMatchResult(MatchState.NONE, null, null);
    }
}
