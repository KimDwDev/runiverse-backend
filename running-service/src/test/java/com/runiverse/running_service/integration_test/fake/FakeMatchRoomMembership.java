package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.match.port.out.MatchRoomMembershipPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.sse.MatchRoomMemberRegistry;

import java.util.HashSet;
import java.util.Set;

// MatchRoomMembershipAdapter를 대신한다 — 방 멤버 등록은 실제 레지스트리에 맡기고,
// Redis 채널 구독은 구독 중인 방 목록으로만 남긴다
public class FakeMatchRoomMembership implements MatchRoomMembershipPort {

    private final MatchRoomMemberRegistry registry;
    private final Set<Long> subscribedRooms = new HashSet<>();

    public FakeMatchRoomMembership(MatchRoomMemberRegistry registry) {
        this.registry = registry;
    }

    // 실제 어댑터처럼 그 방의 첫 참가자일 때만 구독한다
    @Override
    public void join(UserId userId, Long runningRoomId) {
        registry.join(userId, runningRoomId).ifPresent(subscribedRooms::add);
    }

    // 마지막 참가자가 빠질 때만 구독을 끊는다
    @Override
    public void leave(UserId userId) {
        registry.leave(userId).ifPresent(subscribedRooms::remove);
    }

    // 검증 전용
    public boolean isSubscribed(Long runningRoomId) {
        return subscribedRooms.contains(runningRoomId);
    }
}
