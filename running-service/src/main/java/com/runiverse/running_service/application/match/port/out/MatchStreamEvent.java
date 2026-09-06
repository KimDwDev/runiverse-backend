package com.runiverse.running_service.application.match.port.out;

public record MatchStreamEvent(MatchEventType type, RoomInfo room) {

    // 갱신·스냅샷은 전부 이쪽이다 — 확정 순간만 started()를 쓴다
    public static MatchStreamEvent updated(MatchEventType type, RoomInfo room) {
        return new MatchStreamEvent(MatchEventType.MATCH_ROOM_UPDATED, room);
    }

    public static MatchStreamEvent started(RoomInfo room) {
        return new MatchStreamEvent(MatchEventType.MATCH_STARTED, room);
    }
}
