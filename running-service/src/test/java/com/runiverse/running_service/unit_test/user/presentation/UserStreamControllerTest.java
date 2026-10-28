package com.runiverse.running_service.unit_test.user.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.stream.CloseUserStreamCommand;
import com.runiverse.running_service.application.user.command.stream.OpenUserStreamCommand;
import com.runiverse.running_service.application.user.port.in.CloseUserStreamUsecase;
import com.runiverse.running_service.application.user.port.in.OpenUserStreamUsecase;
import com.runiverse.running_service.presentation.user.controller.UserStreamController;
import com.runiverse.running_service.presentation.user.sse.SseUserStreamConnection;
import com.runiverse.running_service.presentation.user.sse.UserStreamProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
@DisplayName("유저 스트림 컨트롤러 단위 테스트")
class UserStreamControllerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private OpenUserStreamUsecase openUserStreamUsecase;

    @Mock
    private CloseUserStreamUsecase closeUserStreamUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserStreamController controller = new UserStreamController(
                openUserStreamUsecase, closeUserStreamUsecase, new UserStreamProperties(Duration.ofMinutes(30)));
        mockMvc = standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JSON_MAPPER))
                .build();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("토큰의 사용자로 SSE 연결을 열고 첫 바이트로 주석 라인을 보낸다")
    void opensStreamForTokenSubject() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/me/stream")).andReturn();

        // then -> 보낼 스냅샷이 없어 주석 라인으로 응답 헤더를 연다
        assertThat(result.getRequest().isAsyncStarted()).isTrue();
        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).startsWith(":connected");
        ArgumentCaptor<OpenUserStreamCommand> opened = ArgumentCaptor.forClass(OpenUserStreamCommand.class);
        verify(openUserStreamUsecase).handle(opened.capture());
        assertThat(opened.getValue().userId()).isEqualTo(USER_ID);
        assertThat(opened.getValue().connection()).isInstanceOf(SseUserStreamConnection.class);
    }

    @Test
    @DisplayName("연결이 끝나면 연 그 연결을 닫기 유스케이스로 넘긴다")
    void closesSameConnectionOnCompletion() throws Exception {
        // given
        MvcResult result = mockMvc.perform(get("/users/me/stream")).andReturn();
        ArgumentCaptor<OpenUserStreamCommand> opened = ArgumentCaptor.forClass(OpenUserStreamCommand.class);
        verify(openUserStreamUsecase).handle(opened.capture());

        // when -> 타임아웃·에러·클라 종료가 모두 이 경로로 모인다
        opened.getValue().connection().closeSuperseded();
        result.getRequest().getAsyncContext().complete();

        // then -> 사람이 아니라 연 그 연결을 넘겨야 새 연결이 지워지지 않는다
        then(closeUserStreamUsecase).should()
                .handle(new CloseUserStreamCommand(USER_ID, opened.getValue().connection()));
    }
}
