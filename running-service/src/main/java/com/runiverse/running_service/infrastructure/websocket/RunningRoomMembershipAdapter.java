package com.runiverse.running_service.infrastructure.websocket;

import com.runiverse.running_service.application.running.port.out.RunningRoomMembershipPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.running.RunningRoomSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 이 인스턴스가 어느 방의 참가자를 들고 있는지 — 방 채널 구독을 켜고 끄는 근거다
@Component
@RequiredArgsConstructor
public class RunningRoomMembershipAdapter implements RunningRoomMembershipPort {

    private final Map<Long, Set<UserId>> usersByRoom = new ConcurrentHashMap<>();
    private final Map<UserId, Long> roomByUser = new ConcurrentHashMap<>();

    private final RunningRoomSubscriber runningRoomSubscriber;

    @Override
    public void join(UserId userId, Long runningRoomId) {
        Long previousRoom = roomByUser.put(userId, runningRoomId);
        if (previousRoom != null && !previousRoom.equals(runningRoomId)) {
            detach(previousRoom, userId);   // 방을 갈아탔으면 이전 방에서 뺀다
        }
        attach(runningRoomId, userId);
    }

    @Override
    public void leave(UserId userId) {
        Long runningRoomId = roomByUser.remove(userId);
        if (runningRoomId != null) {
            detach(runningRoomId, userId);
        }
    }

    @Override
    public Set<UserId> usersIn(Long runningRoomId) {
        // 원본을 그대로 주면 호출자가 명부를 바꿀 수 있다 — 복사본을 준다
        return Set.copyOf(usersByRoom.getOrDefault(runningRoomId, Set.of()));
    }

    // 그 방의 첫 참가자를 받은 순간에만 구독한다
    private void attach(Long runningRoomId, UserId userId) {
        usersByRoom.compute(runningRoomId, (key, users) -> {
            if (users == null) {
                runningRoomSubscriber.subscribe(runningRoomId);
                users = ConcurrentHashMap.newKeySet();
            }
            users.add(userId);
            return users;
        });
    }

    // 마지막 참가자가 빠지면 구독을 끊고 빈 Set도 지운다 - 안 지우면 방 수만큼 샌다
    private void detach(Long runningRoomId, UserId userId) {
        usersByRoom.computeIfPresent(runningRoomId, (key, users) -> {
            users.remove(userId);
            if (!users.isEmpty()) {
                return users;
            }
            runningRoomSubscriber.unsubscribe(runningRoomId);
            return null;
        });
    }
}
