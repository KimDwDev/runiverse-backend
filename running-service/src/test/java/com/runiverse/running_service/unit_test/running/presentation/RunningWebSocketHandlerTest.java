package com.runiverse.running_service.unit_test.running.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.presentation.common.security.JwtHandshakeInterceptor;
import com.runiverse.running_service.presentation.common.websocket.WebSocketEnvelope;
import com.runiverse.running_service.presentation.running.websocket.RunningWebSocketHandler;
import com.runiverse.running_service.presentation.running.websocket.message.RunningMessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
// 로그가 userId를 항상 읽지만 분기마다 호출 횟수가 달라 stubbing 검사는 끈다
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("러닝 WebSocket 핸들러 단위 테스트")
class RunningWebSocketHandlerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private WebSocketSession session;

    private RunningWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RunningWebSocketHandler(jsonMapper);
        given(session.getAttributes()).willReturn(Map.of(
                JwtHandshakeInterceptor.USER_ID, new UserId(UuidCreator.getTimeOrderedEpoch())
        ));
    }

    @Test
    @DisplayName("HEALTH_CHECK를 보내면 HEALTH_CHECKED로 응답한다")
    void respondsHealthChecked() throws Exception {
        // when
        handler.handleMessage(session, text("""
                {"event":"HEALTH_CHECK","data":{}}"""));

        // then
        WebSocketEnvelope sent = captureSent();
        assertThat(sent.event()).isEqualTo("HEALTH_CHECKED");
        assertThat((Map<?, ?>) sent.data()).isEmpty();
    }

    @Test
    @DisplayName("JSON이 깨져 봉투를 읽지 못하면 MALFORMED_MESSAGE로 응답한다")
    void respondsMalformedMessage() throws Exception {
        // when
        handler.handleMessage(session, text("this is not json"));

        // then
        assertThatError(captureSent(), "MALFORMED_MESSAGE", null);
    }

    @Test
    @DisplayName("event가 없으면 MISSING_MESSAGE_TYPE으로 응답한다")
    void respondsMissingMessageType() throws Exception {
        // when
        handler.handleMessage(session, text("""
                {"data":{}}"""));

        // then
        assertThatError(captureSent(), "MISSING_MESSAGE_TYPE", null);
    }

    @Test
    @DisplayName("event가 공백뿐이면 MISSING_MESSAGE_TYPE으로 응답한다")
    void respondsMissingMessageTypeOnBlank() throws Exception {
        // when
        handler.handleMessage(session, text("""
                {"event":"   ","data":{}}"""));

        // then
        assertThatError(captureSent(), "MISSING_MESSAGE_TYPE", null);
    }

    @Test
    @DisplayName("모르는 event면 UNSUPPORTED_MESSAGE_TYPE과 함께 받은 event를 sourceType으로 돌려준다")
    void respondsUnsupportedMessageType() throws Exception {
        // when
        handler.handleMessage(session, text("""
                {"event":"MATCH_REQUEST","data":{}}"""));

        // then
        assertThatError(captureSent(), "UNSUPPORTED_MESSAGE_TYPE", "MATCH_REQUEST");
    }

    @Test
    @DisplayName("S→C 전용 타입을 클라가 보내면 UNSUPPORTED_MESSAGE_TYPE으로 응답한다")
    void rejectsServerToClientType() throws Exception {
        // given -> HEALTH_CHECKED는 서버만 보내는 타입이다
        // when
        handler.handleMessage(session, text("""
                {"event":"HEALTH_CHECKED","data":{}}"""));

        // then
        assertThatError(captureSent(), "UNSUPPORTED_MESSAGE_TYPE", "HEALTH_CHECKED");
    }

    // 새 메시지 타입이 생겨도 연결이 끊기지 않는지 전수로 확인한다
    @ParameterizedTest(name = "{0}")
    @EnumSource(RunningMessageType.class)
    @DisplayName("어떤 타입을 받아도 응답을 보내고 연결을 끊지 않는다")
    void neverClosesConnection(RunningMessageType type) throws Exception {
        // when
        handler.handleMessage(session, text("""
                {"event":"%s","data":{}}""".formatted(type.name())));

        // then
        assertThat(captureSent()).isNotNull();
        verify(session, never()).close();
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("잘못된 메시지를 받아도 연결을 끊지 않는다")
    void neverClosesConnectionOnMalformedMessage() throws Exception {
        // when
        handler.handleMessage(session, text("{"));

        // then
        verify(session, never()).close();
        verify(session, never()).close(any(CloseStatus.class));
    }

    private TextMessage text(String payload) {
        return new TextMessage(payload);
    }

    private WebSocketEnvelope captureSent() throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        return jsonMapper.readValue(captor.getValue().getPayload(), WebSocketEnvelope.class);
    }

    private void assertThatError(WebSocketEnvelope sent, String code, String sourceType) {
        assertThat(sent.event()).isEqualTo("ERROR");
        Map<?, ?> data = (Map<?, ?>) sent.data();
        assertThat(data.get("code")).isEqualTo(code);
        assertThat(data.get("sourceType")).isEqualTo(sourceType);
    }
}
