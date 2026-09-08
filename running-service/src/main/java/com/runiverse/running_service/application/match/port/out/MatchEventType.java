package com.runiverse.running_service.application.match.port.out;

// 둘 다 data는 RoomInfo로 같다 — 이름이 "전이"와 "상태"를 가른다(api-spec 5-B).
// MATCH_STARTED는 확정된 그 순간 한 번, 나머지 갱신과 연결 직후 스냅샷은 MATCH_ROOM_UPDATED다
public enum MatchEventType {
    MATCH_STARTED,
    MATCH_ROOM_UPDATED
}
