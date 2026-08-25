package com.runiverse.running_service.unit_test.running.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.session.RegisterRunningSessionHandler;
import com.runiverse.running_service.application.running.command.session.RemoveRunningSessionHandler;
import com.runiverse.running_service.application.running.command.start.StartRunningCommand;
import com.runiverse.running_service.application.running.command.start.StartRunningResult;
import com.runiverse.running_service.application.running.exception.RunningRoomNotFoundException;
import com.runiverse.running_service.application.running.port.in.StartRunningUsecase;
import com.runiverse.running_service.application.running.port.out.PublishSupersedePort;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.application.running.port.out.RunningRoomMembershipPort;
import com.runiverse.running_service.infrastructure.websocket.RunningSessionRegistryAdapter;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
// 로그가 userId를 항상 읽지만 분기마다 호출 횟수가 달라 stubbing 검사는 끈다
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("러닝 WebSocket 핸들러 단위 테스트")
class RunningWebSocketHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long ROOM_ID = 125L;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private WebSocketSession session;

    // 같은 유저의 두 번째 기기 — 중복 연결 테스트에서만 쓴다
    @Mock
    private WebSocketSession other;

    @Mock
    private StartRunningUsecase startRunningUsecase;

    @Mock
    private PublishSupersedePort publishSupersedePort;

    // 방 합류는 Redis 구독을 건드리므로 가짜로 둔다
    @Mock
    private RunningRoomMembershipPort runningRoomMembershipPort;

    private RunningWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        // 소켓 명부와 등록·해제 유스케이스는 상태만 들고 있는 POJO라 실제 구현을 쓴다
        // — 중복 연결 판정이 진짜로 도는지 봐야 한다. 인스턴스 밖으로 나가는 것만 가짜다
        RunningSessionPort sessionPort = new RunningSessionRegistryAdapter();
        handler = new RunningWebSocketHandler(
                jsonMapper,
                startRunningUsecase,
                new RegisterRunningSessionHandler(sessionPort, runningRoomMembershipPort, publishSupersedePort),
                new RemoveRunningSessionHandler(sessionPort, runningRoomMembershipPort));
        given(session.getId()).willReturn("session-1");
        given(session.getAttributes()).willReturn(authenticated());
        given(other.getId()).willReturn("session-2");
        given(other.getAttributes()).willReturn(authenticated());
    }

    // 핸드셰이크 인터셉터가 채워 넣는 값
    private static Map<String, Object> authenticated() {
        return Map.of(JwtHandshakeInterceptor.USER_ID, new UserId(USER_ID));
    }

    private static TextMessage runningStart(String data) {
        return new TextMessage("""
                {"event":"RUNNING_START","data":%s}""".formatted(data));
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

    @Test
    @DisplayName("RUNNING_START를 보내면 유스케이스를 태우고 RUNNING_STARTED로 응답한다")
    void respondsRunningStarted() throws Exception {
        // given
        given(startRunningUsecase.handle(new StartRunningCommand(USER_ID, ROOM_ID)))
                .willReturn(new StartRunningResult(ROOM_ID));

        // when
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // then
        WebSocketEnvelope sent = captureSent();
        assertThat(sent.event()).isEqualTo("RUNNING_STARTED");
    }

    @Test
    @DisplayName("runningRoomId가 없으면 유스케이스를 태우지 않고 INVALID_REQUEST로 응답한다")
    void respondsInvalidRequestWithoutRoomId() throws Exception {
        // when -> WS에는 @Valid 파이프라인이 없어 핸들러가 직접 걸러야 한다
        handler.handleMessage(session, runningStart("{}"));

        // then
        assertThatError(captureSent(), "INVALID_REQUEST", "RUNNING_START");
        verifyNoInteractions(startRunningUsecase);
    }

    @Test
    @DisplayName("유스케이스가 튕겨내면 그 에러 코드를 ERROR로 돌려준다")
    void respondsUsecaseErrorCode() throws Exception {
        // given
        given(startRunningUsecase.handle(any())).willThrow(new RunningRoomNotFoundException());

        // when
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // then -> 코드·문구의 정본은 application ErrorCode다
        assertThatError(captureSent(), "ROOM_NOT_FOUND", "RUNNING_START");
    }

    @Test
    @DisplayName("같은 유저가 다른 기기로 들어오면 이전 연결을 4001로 닫는다")
    void closesSupersededSession() throws Exception {
        // given -> 첫 기기가 이미 붙어 있다
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when -> 기기를 바꿔 다시 들어온다
        handler.handleMessage(other, runningStart("""
                {"runningRoomId":125}"""));

        // then -> 마지막 것이 이긴다. 두 소켓이 살아 있으면 좌표 트랙이 섞인다
        ArgumentCaptor<CloseStatus> captor = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(4001);
        assertThat(captureSent(other).event()).isEqualTo("RUNNING_STARTED");
    }

    @Test
    @DisplayName("등록에 성공하면 다른 인스턴스가 옛 연결을 닫도록 통지한다")
    void publishesSupersedeNotification() throws Exception {
        // given
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID));

        // when
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // then -> 밀어낼 옛 연결이 이 인스턴스에 없어도 다른 인스턴스에는 남아 있을 수 있다
        verify(publishSupersedePort).publish(USER_ID, ROOM_ID, "session-1");
    }

    @Test
    @DisplayName("유스케이스가 실패하면 기존 연결을 끊지 않는다")
    void keepsPreviousSessionWhenUsecaseFails() throws Exception {
        // given -> 첫 기기는 성공, 두 번째 요청은 유스케이스가 튕겨낸다
        given(startRunningUsecase.handle(any()))
                .willReturn(new StartRunningResult(ROOM_ID))
                .willThrow(new RunningRoomNotFoundException());
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(other, runningStart("""
                {"runningRoomId":999}"""));

        // then -> 잘못된 요청 하나가 멀쩡히 뛰는 기기를 끊어서는 안 된다
        verify(session, never()).close(any(CloseStatus.class));
        assertThatError(captureSent(other), "ROOM_NOT_FOUND", "RUNNING_START");
    }

    @Test
    @DisplayName("같은 소켓이 RUNNING_START를 두 번 보내도 자기 자신을 끊지 않는다")
    void doesNotCloseItselfOnResend() throws Exception {
        // given -> 재연결 뒤 클라가 같은 메시지를 다시 보내는 정상 경로
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // then
        verify(session, never()).close(any(CloseStatus.class));
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
        return captureSent(session);
    }

    private WebSocketEnvelope captureSent(WebSocketSession target) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(target).sendMessage(captor.capture());
        return jsonMapper.readValue(captor.getValue().getPayload(), WebSocketEnvelope.class);
    }

    private void assertThatError(WebSocketEnvelope sent, String code, String sourceType) {
        assertThat(sent.event()).isEqualTo("ERROR");
        Map<?, ?> data = (Map<?, ?>) sent.data();
        assertThat(data.get("code")).isEqualTo(code);
        assertThat(data.get("sourceType")).isEqualTo(sourceType);
    }
}
