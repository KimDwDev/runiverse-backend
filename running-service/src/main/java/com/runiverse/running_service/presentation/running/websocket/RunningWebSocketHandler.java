package com.runiverse.running_service.presentation.running.websocket;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.presentation.common.security.JwtHandshakeInterceptor;
import com.runiverse.running_service.presentation.common.websocket.WebSocketEnvelope;
import com.runiverse.running_service.presentation.running.websocket.message.ErrorPayload;
import com.runiverse.running_service.presentation.running.websocket.message.RunningMessageType;
import com.runiverse.running_service.presentation.running.websocket.message.RunningWebSocketErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunningWebSocketHandler extends TextWebSocketHandler {

    private final JsonMapper jsonMapper;

    // 웹소켓 연결이 성공한 직후 한번 호출
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("러닝 WebSocket 연결 — userId={}, sessionId={}", userId(session), session.getId());
    }

    // event 메시지 보내서 실제 이벤트 핸들에 도착하기전 메시지
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        WebSocketEnvelope envelope;
        try {
            envelope = jsonMapper.readValue(message.getPayload(), WebSocketEnvelope.class);
        } catch (JacksonException e) {
            log.warn("러닝 WebSocket 봉투 파싱 실패 — userId={}", userId(session));
            sendError(session, RunningWebSocketErrorCode.MALFORMED_MESSAGE, null);
            return;
        }
        // 봉투({type,data}) 파싱·디스패치는 다음 단계 — 지금은 수신 확인만 한다.
        // 위치 좌표가 실려 오므로 payload는 개인정보다. INFO로 남기지 않는다.
        log.debug("러닝 WebSocket 수신 — userId={}, payload={}", userId(session), message.getPayload());
        String event = envelope.event();
// from()은 null에도 empty를 돌려줘 UNSUPPORTED와 구분이 안 된다 — 여기서 먼저 가른다
        if (event == null || event.isBlank()) {
            sendError(session, RunningWebSocketErrorCode.MISSING_MESSAGE_TYPE, null);
            return;
        }
        RunningMessageType type = RunningMessageType.from(event).orElse(null);
        if (type == null) {
            sendError(session, RunningWebSocketErrorCode.UNSUPPORTED_MESSAGE_TYPE, envelope.event());
            return;
        }
        switch (type) {
            case HEALTH_CHECK -> send(session, RunningMessageType.HEALTH_CHECKED.message());
            // HEALTH_CHECKED·SERVER_ERROR는 S→C 전용 — 클라가 보내면 처리 대상이 아니다.
            default -> sendError(session, RunningWebSocketErrorCode.UNSUPPORTED_MESSAGE_TYPE, event);
        }
    }

    private void sendError(
            WebSocketSession session,
            RunningWebSocketErrorCode errorCode,
            String sourceType
    ) throws IOException {
        send(session, RunningMessageType.SERVER_ERROR.message(ErrorPayload.of(errorCode, sourceType)));
    }

    private void send(WebSocketSession session, WebSocketEnvelope envelope) throws IOException {
        session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(envelope)));
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
