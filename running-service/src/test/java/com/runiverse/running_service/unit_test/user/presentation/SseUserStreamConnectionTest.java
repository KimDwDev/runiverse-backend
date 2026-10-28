package com.runiverse.running_service.unit_test.user.presentation;

import com.runiverse.running_service.presentation.user.sse.SseUserStreamConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("유저 스트림 SSE 전송 단위 테스트")
class SseUserStreamConnectionTest {

    @Mock
    private SseEmitter emitter;

    private SseUserStreamConnection connection;

    @BeforeEach
    void setUp() {
        connection = new SseUserStreamConnection("conn-1", emitter);
    }

    @Test
    @DisplayName("keep-alive는 이벤트가 아니라 주석 라인을 보낸다")
    void keepAliveSendsComment() throws Exception {
        // when
        connection.keepAlive();

        // then -> 클라 이벤트 핸들러가 깨지 않는다
        ArgumentCaptor<SseEmitter.SseEventBuilder> sent = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(sent.capture());
        String frame = sent.getValue().build().stream()
                .map(ResponseBodyEmitter.DataWithMediaType::getData)
                .map(String::valueOf)
                .reduce("", String::concat);
        assertThat(frame).startsWith(":ping");
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
