package com.runiverse.running_service.presentation.running.websocket;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;
import com.runiverse.running_service.application.running.command.session.RegisterRunningSessionCommand;
import com.runiverse.running_service.application.running.command.session.RemoveRunningSessionCommand;
import com.runiverse.running_service.application.running.command.start.StartRunningCommand;
import com.runiverse.running_service.application.running.port.in.RegisterRunningSessionUsecase;
import com.runiverse.running_service.application.running.port.in.RemoveRunningSessionUsecase;
import com.runiverse.running_service.application.running.port.in.StartRunningUsecase;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.presentation.common.security.JwtHandshakeInterceptor;
import com.runiverse.running_service.presentation.common.websocket.WebSocketEnvelope;
import com.runiverse.running_service.presentation.running.websocket.message.ErrorPayload;
import com.runiverse.running_service.presentation.running.websocket.message.RunningMessageType;
import com.runiverse.running_service.presentation.running.websocket.message.RunningStartRequest;
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
    private final StartRunningUsecase startRunningUsecase;
    private final RegisterRunningSessionUsecase registerRunningSessionUsecase;
    private final RemoveRunningSessionUsecase removeRunningSessionUsecase;

    // 웹소켓 연결이 성공한 직후 한번 호출
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 연결만으로는 아무것도 등록하지 않는다 — 어느 방인지는 RUNNING_START가 정한다
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
            case RUNNING_START -> handleRunningStart(session, envelope);
            // HEALTH_CHECKED·RUNNING_STARTED·ERROR는 S→C 전용 — 클라가 보내면 처리 대상이 아니다.
            default -> sendError(session, RunningWebSocketErrorCode.UNSUPPORTED_MESSAGE_TYPE, event);
        }
    }

    // 채널 등록·재입장·방 시작·참가자 시작을 한 번에 처리한다
    private void handleRunningStart(WebSocketSession session, WebSocketEnvelope envelope)
            throws IOException {
        RunningStartRequest request;
        try {
            request = jsonMapper.convertValue(envelope.data(), RunningStartRequest.class);
        } catch (JacksonException | IllegalArgumentException e) {
            sendError(session, RunningWebSocketErrorCode.INVALID_REQUEST, envelope.event());
            return;
        }
        if (request == null || !request.isValid()) {
            sendError(session, RunningWebSocketErrorCode.INVALID_REQUEST, envelope.event());
            return;
        }
        UserId userId = userId(session);
        try {
            startRunningUsecase.handle(
                    new StartRunningCommand(userId.value(), request.runningRoomId()));
        } catch (BusinessException e) {
            // 유스케이스가 튕겨낸 것만 코드로 내보낸다.
            // 도메인 예외가 여기까지 오면 핸들러의 선검사가 샌 것이라 잡지 않는다
            sendError(session, e.getErrorCode(), envelope.event());
            return;
        }
        // 실패한 요청으로 남의 기기를 끊지 않도록 성공한 뒤에 등록한다
        registerRunningSessionUsecase.handle(new RegisterRunningSessionCommand(
                userId.value(), request.runningRoomId(), new WebSocketRunningConnection(session)));
        send(session, RunningMessageType.RUNNING_STARTED.message());
    }

    private void sendError(
            WebSocketSession session,
            RunningWebSocketErrorCode errorCode,
            String sourceType
    ) throws IOException {
        send(session, RunningMessageType.ERROR.message(ErrorPayload.of(errorCode, sourceType)));
    }

    private void sendError(
            WebSocketSession session,
            ErrorCode errorCode,
            String sourceType
    ) throws IOException {
        send(session, RunningMessageType.ERROR.message(ErrorPayload.of(errorCode, sourceType)));
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
        UserId userId = userId(session);
        // 연결 끊김 ≠ 방 나가기 — running_room_sessions.is_connected는 여기서 건드리지 않는다.
        // 명부는 접속 여부라 여기서 지운다
        removeRunningSessionUsecase.handle(
                new RemoveRunningSessionCommand(userId.value(), new WebSocketRunningConnection(session)));
        log.info("러닝 WebSocket 종료 — userId={}, status={}", userId(session), status);
    }

    private UserId userId(WebSocketSession session) {
        return (UserId) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID);
    }
}
