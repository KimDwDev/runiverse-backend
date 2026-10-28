package com.runiverse.running_service.unit_test.user.presentation;

import com.runiverse.running_service.application.running.query.status.GetUserStatusResult;
import com.runiverse.running_service.application.running.query.status.UserRunningStatus;
import com.runiverse.running_service.application.user.port.out.UserStreamEvent;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.presentation.user.response.UserStatusResponse;
import com.runiverse.running_service.presentation.user.sse.SseUserStreamConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

// 와이어 계약 — 이벤트 이름과 data 형태만 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("유저 스트림 SSE 전송 단위 테스트")
class SseUserStreamConnectionTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 28, 19, 0);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private SseEmitter emitter;

    private SseUserStreamConnection connection;

    @BeforeEach
    void setUp() {
        connection = new SseUserStreamConnection("conn-1", emitter);
    }

    private SseEmitter.SseEventBuilder sentEvent() throws Exception {
        ArgumentCaptor<SseEmitter.SseEventBuilder> sent = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(sent.capture());
        return sent.getValue();
    }

    // SSE 틀("event:"·"data:")을 이어 붙인 문자열 — 이벤트 이름과 주석 라인을 본다
    private static String frameText(Set<ResponseBodyEmitter.DataWithMediaType> parts) {
        StringBuilder text = new StringBuilder();
        parts.stream()
                .filter(part -> !MediaType.APPLICATION_JSON.equals(part.getMediaType()))
                .forEach(part -> text.append(part.getData()));
        return text.toString();
    }

    private static Object jsonBody(Set<ResponseBodyEmitter.DataWithMediaType> parts) {
        return parts.stream()
                .filter(part -> MediaType.APPLICATION_JSON.equals(part.getMediaType()))
                .map(ResponseBodyEmitter.DataWithMediaType::getData)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("상태 스냅샷은 USER_STATUS 이름으로 2-2와 같은 형식을 싣는다")
    void statusSnapshotUsesSameShapeAsRest() throws Exception {
        // given
        GetUserStatusResult status = new GetUserStatusResult(
                UserRunningStatus.READY, RunningRoomType.INVITE, 130L, START, 7_000, null);

        // when
        connection.send(UserStreamEvent.status(status));

        // then
        Set<ResponseBodyEmitter.DataWithMediaType> parts = sentEvent().build();
        assertThat(frameText(parts)).contains("event:USER_STATUS");
        Object body = jsonBody(parts);
        assertThat(body).isInstanceOf(UserStatusResponse.class);
        JsonNode json = JSON.valueToTree(body);
        assertThat(json.get("status").asString()).isEqualTo("READY");
        assertThat(json.get("type").asString()).isEqualTo("INVITE");
        assertThat(json.get("runningRoomId").asLong()).isEqualTo(130L);
        assertThat(json.get("targetDistanceMeters").asInt()).isEqualTo(7_000);
    }

    @Test
    @DisplayName("IDLE이면 방에 딸린 값은 비고 쿨다운만 남는다")
    void idleSnapshotCarriesOnlyCooldown() throws Exception {
        // given
        LocalDateTime cooldownUntil = START.plusMinutes(20);

        // when
        connection.send(UserStreamEvent.status(GetUserStatusResult.idle(cooldownUntil)));

        // then
        JsonNode json = JSON.valueToTree(jsonBody(sentEvent().build()));
        assertThat(json.get("status").asString()).isEqualTo("IDLE");
        assertThat(json.get("runningRoomId").isNull()).isTrue();
        assertThat(json.get("cooldownUntil").isNull()).isFalse();
    }

    @Test
    @DisplayName("keep-alive는 이벤트가 아니라 주석 라인을 보낸다")
    void keepAliveSendsComment() throws Exception {
        // when
        connection.keepAlive();

        // then -> 클라 이벤트 핸들러가 깨지 않는다
        assertThat(frameText(sentEvent().build())).startsWith(":ping");
    }

    @Test
    @DisplayName("전송이 실패하면 스스로 닫아 레지스트리에서 빠지게 한다")
    void completesWhenSendFails() throws Exception {
        // given -> 끊긴 단말이다
        willThrow(new IOException("broken pipe")).given(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // when
        connection.keepAlive();

        // then -> complete가 onCompletion을 돌려 정리가 한 곳으로 모인다
        verify(emitter).complete();
    }

    @Test
    @DisplayName("밀려난 연결은 닫힌다")
    void closesWhenSuperseded() {
        // when
        connection.closeSuperseded();

        // then
        verify(emitter).complete();
    }
}
