package com.runiverse.running_service.infrastructure.redis.match;

import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.RoomInfo;

// 대상 유저는 싣지 않는다 — 받는 인스턴스가 자기 명부에서 꺼낸다
public record MatchEventMessage(MatchEventType type, RoomInfo room) {

}
