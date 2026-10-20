package com.runiverse.running_service.unit_test.user.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.port.in.GetProfileImageUsecase;
import com.runiverse.running_service.application.user.port.in.GetUserProfileUsecase;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlQuery;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlResult;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.user.controller.UserController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// 경로의 userId는 컨트롤러가 거른다 — 유스케이스로 넘어가면 VO 검증에서 500이 된다.
// 주제와 무관한 유스케이스는 생성자에 null로 들어간다
@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 컨트롤러 단위 테스트")
class UserControllerTest {

    private static final UUID VIEWER_ID = UuidCreator.getTimeOrderedEpoch();
    // 형식은 UUID지만 서버가 발급하지 않는 버전이다
    private static final String NON_V7_USER_ID = "00000000-0000-4000-8000-000000000000";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private GetUserProfileUsecase getUserProfileUsecase;

    @Mock
    private GetProfileImageUsecase getProfileImageUsecase;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JSON_MAPPER))
                .build();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(VIEWER_ID.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("프로필 사진 조회: 경로의 v7 userId를 그대로 유스케이스에 넘긴다")
    void 프로필_사진_조회는_경로의_userId를_넘긴다() throws Exception {
        // given -> userId가 다르게 바인딩되면 이 스텁이 맞지 않아 빈 응답이 된다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(getProfileImageUsecase.handle(new GetProfileImageUrlQuery(userId)))
                .willReturn(new GetProfileImageUrlResult(userId, "https://cdn.example.com/profile.jpg"));

        // when
        MvcResult result = mockMvc.perform(get("/users/" + userId + "/profile-image")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).containsEntry("profileImageUrl", "https://cdn.example.com/profile.jpg");
    }

    @Test
    @DisplayName("프로필 조회: v7이 아닌 userId는 400이고 조회하지 않는다")
    void 프로필_조회는_v7이_아닌_userId에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/" + NON_V7_USER_ID)).andReturn();

        // then
        assertInvalidRequest(result);
        then(getUserProfileUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("프로필 조회: 자리 수가 모자란 userId는 400이다 -> UUID 변환은 이 모양도 받아 준다")
    void 프로필_조회는_짧은_userId에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/1-1-1-1-1")).andReturn();

        // then
        assertInvalidRequest(result);
        then(getUserProfileUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("프로필 조회: UUID가 아닌 userId도 400 기본 문구다")
    void 프로필_조회는_UUID가_아닌_userId에_기본_문구다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/abc")).andReturn();

        // then
        assertInvalidRequest(result);
        then(getUserProfileUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("프로필 사진 조회: v7이 아닌 userId는 400이고 조회하지 않는다")
    void 프로필_사진_조회는_v7이_아닌_userId에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/" + NON_V7_USER_ID + "/profile-image")).andReturn();

        // then
        assertInvalidRequest(result);
        then(getProfileImageUsecase).shouldHaveNoInteractions();
    }

    private static void assertInvalidRequest(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
    }

    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }
}
