package com.runiverse.running_service.presentation.running.websocket;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.presentation.common.security.JwtHandshakeInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class RunningWebsocketHandler extends TextWebSocketHandler {

    // 웹소켓 연결이 성공한 직후 한번 호출
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("러닝 WebSocket 연결 — userId={}, sessionId={}", userId(session), session.getId());
    }

    // event 메시지 보내서 실제 이벤트 핸들에 도착하기전 메시지
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 봉투({type,data}) 파싱·디스패치는 다음 단계 — 지금은 수신 확인만 한다.
        // 위치 좌표가 실려 오므로 payload는 개인정보다. INFO로 남기지 않는다.
        log.debug("러닝 WebSocket 수신 — userId={}, payload={}", userId(session), message.getPayload());
    }

    // 통신 과정에서 오류가 발생하면 처리
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("러닝 WebSocket 전송 오류 — userId={}", userId(session), exception);
    }

    // 연결이 끊겼을때 처리
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 연결 끊김 ≠ 방 나가기 — running_room_sessions.is_connected는 여기서 건드리지 않는다.
        log.info("러닝 WebSocket 종료 — userId={}, status={}", userId(session), status);
    }

    private UserId userId(WebSocketSession session) {
        return (UserId) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID);
    }
}
