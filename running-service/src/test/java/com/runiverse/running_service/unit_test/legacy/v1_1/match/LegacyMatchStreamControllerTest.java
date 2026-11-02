package com.runiverse.running_service.unit_test.legacy.v1_1.match;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamCommand;
import com.runiverse.running_service.application.match.exception.ActiveMatchNotFoundException;
import com.runiverse.running_service.application.match.port.in.CloseMatchStreamUsecase;
import com.runiverse.running_service.application.match.port.in.OpenMatchStreamUsecase;
import com.runiverse.running_service.legacy.v1_1.match.controller.LegacyMatchStreamController;
import com.runiverse.running_service.legacy.v1_1.match.sse.LegacySseMatchStreamConnection;
import com.runiverse.running_service.presentation.match.sse.MatchStreamProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// SSE 응답은 MockMvc 비동기 처리가 필요해 컨트롤러를 직접 부른다 — 무엇으로 연결을 여는지와 응답 상태만 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("1.1.x 매칭 스트림 컨트롤러 단위 테스트")
class LegacyMatchStreamControllerTest {

    // UserId VO가 UUIDv7만 받는다
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    @Mock
    private OpenMatchStreamUsecase openMatchStreamUsecase;

    @Mock
    private CloseMatchStreamUsecase closeMatchStreamUsecase;

    private LegacyMatchStreamController controller;

    @BeforeEach
    void setUp() {
        controller = new LegacyMatchStreamController(
                openMatchStreamUsecase, closeMatchStreamUsecase, new MatchStreamProperties(TIMEOUT));
    }

    @Test
    @DisplayName("옛 경로 GET /running-matches/stream으로 받는다")
    void mapsLegacyPath() throws Exception {
        // when & then -> 1.1.x 앱이 부르는 경로가 바뀌면 404가 된다
        assertThat(LegacyMatchStreamController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("running-matches");
        assertThat(LegacyMatchStreamController.class.getMethod("stream", Jwt.class)
                .getAnnotation(GetMapping.class).path())
                .containsExactly("stream");
    }

    @Test
    @DisplayName("토큰 주체로 1.1.x 형식 연결을 열고 설정한 타임아웃의 스트림을 200으로 돌려준다")
    void opensLegacyConnection() throws Exception {
        // when
        ResponseEntity<SseEmitter> response = controller.stream(jwt());

        // then -> 1.2.0 연결(SseMatchStreamConnection)로 열면 이벤트 이름이 1.2.0 것으로 나간다
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTimeout()).isEqualTo(TIMEOUT.toMillis());
        ArgumentCaptor<OpenMatchStreamCommand> opened = ArgumentCaptor.forClass(OpenMatchStreamCommand.class);
        verify(openMatchStreamUsecase).handle(opened.capture());
        assertThat(opened.getValue().userId()).isEqualTo(USER_ID);
        assertThat(opened.getValue().connection()).isInstanceOf(LegacySseMatchStreamConnection.class);
    }

    @Test
    @DisplayName("활성 신청이 없으면 1.1.1 서버처럼 본문 없는 404다")
    void noActiveMatchIsNotFound() throws Exception {
        // given
        willThrow(new ActiveMatchNotFoundException()).given(openMatchStreamUsecase).handle(any());

        // when
        ResponseEntity<SseEmitter> response = controller.stream(jwt());

        // then -> 열지 못한 연결이라 정리할 것도 없다
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNull();
        verifyNoInteractions(closeMatchStreamUsecase);
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .build();
    }
}
