package com.runiverse.running_service.unit_test.relationship.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestResult;
import com.runiverse.running_service.application.relationship.exception.FriendRequestNotFoundException;
import com.runiverse.running_service.application.relationship.port.in.AcceptFriendRequestUsecase;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.relationship.controller.RelationshipController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
@DisplayName("관계 컨트롤러 단위 테스트")
class RelationshipControllerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID REQUESTER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private AcceptFriendRequestUsecase acceptFriendRequestUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new RelationshipController(acceptFriendRequestUsecase))
                .setControllerAdvice(new GlobalExceptionHandler())
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
    @DisplayName("요청 수락: 수락자는 토큰에서, 요청자는 경로에서 꺼내 넘기고 200으로 FRIEND를 준다")
    void acceptFriendRequest() throws Exception {
        // given -> 두 값이 뒤바뀌어 넘어가면 이 스텁이 맞지 않아 빈 응답이 된다
        given(acceptFriendRequestUsecase.handle(new AcceptFriendRequestCommand(USER_ID, REQUESTER_ID)))
                .willReturn(new AcceptFriendRequestResult(RelationshipType.FRIEND));

        // when
        MvcResult result = mockMvc.perform(put("/users/{userId}/friend-request", REQUESTER_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).containsExactly(Map.entry("relationType", "FRIEND"));
    }

    @Test
    @DisplayName("요청 수락: 수락할 요청이 없으면 404 NOT_FOUND다")
    void acceptFriendRequestNotFound() throws Exception {
        // given
        given(acceptFriendRequestUsecase.handle(new AcceptFriendRequestCommand(USER_ID, REQUESTER_ID)))
                .willThrow(new FriendRequestNotFoundException());

        // when
        MvcResult result = mockMvc.perform(put("/users/{userId}/friend-request", REQUESTER_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result))
                .containsEntry("code", "NOT_FOUND")
                .containsEntry("message", "요청한 리소스를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("요청 수락: UUIDv7이 아닌 경로는 400이고 유스케이스를 부르지 않는다")
    void acceptFriendRequestWithInvalidUserId() throws Exception {
        // when -> UUID 타입으로 받으면 변환되는 값이라 형식 검증이 막아야 한다
        MvcResult result = mockMvc.perform(put("/users/{userId}/friend-request", "1-1-1-1-1")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
        then(acceptFriendRequestUsecase).shouldHaveNoInteractions();
    }

    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }
}
