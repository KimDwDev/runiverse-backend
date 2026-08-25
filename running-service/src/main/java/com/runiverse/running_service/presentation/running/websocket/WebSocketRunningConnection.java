package com.runiverse.running_service.presentation.running.websocket;

import com.runiverse.running_service.application.running.port.out.RunningConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

public record WebSocketRunningConnection(WebSocketSession session) implements RunningConnection {

    private static final Logger log = LoggerFactory.getLogger(WebSocketRunningConnection.class);
    // 이 코드를 받은 클라는 재연결 하지 않는다.
    private static final CloseStatus SUPERSEDED = new CloseStatus(4001, "다른 연결이 이어받았습니다.");

    @Override
    public String id() {
        return session.getId();
    }

    @Override
    public void closeSuperseded() {
        try {
            session.close(SUPERSEDED);
        } catch (IOException e) {
            log.warn("밀려난 러닝 WebSocket 종료 실패 — sessionId={}", session.getId(), e);
        }
    }
}
