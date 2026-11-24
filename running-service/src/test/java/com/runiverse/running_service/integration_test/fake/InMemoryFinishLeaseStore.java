package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.FinishLeasePort;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Redis 끝내기 임대의 메모리판 — TTL은 흉내 내지 않는다(만료는 어댑터·Redis의 몫이다)
public class InMemoryFinishLeaseStore implements FinishLeasePort {

    private final Map<String, String> leases = new ConcurrentHashMap<>();

    @Override
    public Optional<String> tryAcquire(Long runningRoomId, UserId userId) {
        String token = UUID.randomUUID().toString();
        return leases.putIfAbsent(key(runningRoomId, userId), token) == null
                ? Optional.of(token) : Optional.empty();
    }

    @Override
    public void release(Long runningRoomId, UserId userId, String token) {
        leases.remove(key(runningRoomId, userId), token);
    }

    // 다른 연결이 끝내는 중인 상황을 만든다
    public void hold(Long runningRoomId, UserId userId) {
        leases.put(key(runningRoomId, userId), "held-by-other");
    }

    public boolean isHeld(Long runningRoomId, UserId userId) {
        return leases.containsKey(key(runningRoomId, userId));
    }

    private static String key(Long runningRoomId, UserId userId) {
        return runningRoomId + ":" + userId.value();
    }
}
