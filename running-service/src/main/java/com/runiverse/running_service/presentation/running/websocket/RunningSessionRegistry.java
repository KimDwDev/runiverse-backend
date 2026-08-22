package com.runiverse.running_service.presentation.running.websocket;

import com.runiverse.running_service.domain.common.vo.UserId;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RunningSessionRegistry {

    private final Map<UserId, WebSocketSession> sessionsByUser = new ConcurrentHashMap<>();

    // 마지막 연결이 이긴다 — 밀려난 이전 연결을 돌려주면 호출자가 닫는다.
    // 새 연결을 거부하는 쪽으로 가면 앱을 강제 종료한 유저가 좀비 소켓 때문에 못 들어온다
    public Optional<WebSocketSession> register(UserId userId, WebSocketSession session) {
        WebSocketSession superseded = sessionsByUser.put(userId, session);
        if (superseded == null || superseded.getId().equals(session.getId())) {
            return Optional.empty();   // 같은 소켓의 재전송이면 밀어낼 것이 없다
        }
        return Optional.of(superseded);
    }

    public void remove(UserId userId, WebSocketSession session) {
        if (userId == null) {
            return;
        }
        // 새 연결이 이미 자리를 가져갔으면 그 매핑까지 지우면 안 된다 — 값이 같을 때만 지운다
        sessionsByUser.remove(userId, session);
    }
}
