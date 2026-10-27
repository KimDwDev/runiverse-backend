package com.runiverse.running_service.unit_test.match.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.invite.command.accept.AcceptInvitationCommand;
import com.runiverse.running_service.application.invite.command.accept.AcceptInvitationResult;
import com.runiverse.running_service.application.invite.command.decline.DeclineInvitationCommand;
import com.runiverse.running_service.application.invite.command.open.OpenInviteRoomCommand;
import com.runiverse.running_service.application.invite.command.open.OpenInviteRoomResult;
import com.runiverse.running_service.application.invite.command.send.InviteFriendsCommand;
import com.runiverse.running_service.application.invite.command.send.InviteFriendsResult;
import com.runiverse.running_service.application.invite.exception.InvalidInviteOptionException;
import com.runiverse.running_service.application.invite.exception.InvitationNotFoundException;
import com.runiverse.running_service.application.invite.exception.InvitationNotPendingException;
import com.runiverse.running_service.application.invite.exception.InviteRoomNotFoundException;
import com.runiverse.running_service.application.invite.exception.InviteStartAtOutOfRangeException;
import com.runiverse.running_service.application.invite.exception.NotFriendException;
import com.runiverse.running_service.application.invite.exception.RunningRoomFullException;
import com.runiverse.running_service.application.invite.port.in.AcceptInvitationUsecase;
import com.runiverse.running_service.application.invite.port.in.DeclineInvitationUsecase;
import com.runiverse.running_service.application.invite.port.in.InviteFriendsUsecase;
import com.runiverse.running_service.application.invite.port.in.OpenInviteRoomUsecase;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomInvitationStatus;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.match.controller.InviteRoomController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// 초대 방 개설·초대·수락·거절 — 경로의 방 ID와 본문 모양은 컨트롤러가 거른다.
// 유스케이스로 넘어가면 VO 검증에서 500이 된다
@ExtendWith(MockitoExtension.class)
@DisplayName("초대 방 컨트롤러 단위 테스트")
class InviteRoomControllerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private OpenInviteRoomUsecase openInviteRoomUsecase;

    @Mock
    private InviteFriendsUsecase inviteFriendsUsecase;

    @Mock
    private AcceptInvitationUsecase acceptInvitationUsecase;

    @Mock
    private DeclineInvitationUsecase declineInvitationUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new InviteRoomController(
                        openInviteRoomUsecase, inviteFriendsUsecase,
                        acceptInvitationUsecase, declineInvitationUsecase))
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
    @DisplayName("초대 방 개설: 토큰 주체와 본문 값을 넘기고 201로 방 ID만 돌려준다")
    void 초대_방_개설은_201로_방_ID를_돌려준다() throws Exception {
        // given -> 시각이 다르게 바인딩되면 이 스텁이 맞지 않아 빈 응답이 된다
        given(openInviteRoomUsecase.handle(new OpenInviteRoomCommand(
                USER_ID, LocalDateTime.of(2026, 10, 26, 19, 30), 7_000)))
                .willReturn(new OpenInviteRoomResult(130L));

        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledStartAt":"2026-10-26T19:30:00","targetDistanceMeters":7000}
                                """))
                .andReturn();

        // then -> 방 정보는 스트림이 나르므로 응답은 ID 하나다
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result)).containsExactly(Map.entry("runningRoomId", 130));
    }

    @Test
    @DisplayName("초대 방 개설: 시작 시각이 빠지면 400이고 유스케이스를 부르지 않는다")
    void 초대_방_개설은_시작_시각이_없으면_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDistanceMeters":7000}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "시작 시각은 필수입니다.");
        then(openInviteRoomUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("초대 방 개설: 목표 거리가 빠지면 400이고 유스케이스를 부르지 않는다")
    void 초대_방_개설은_목표_거리가_없으면_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledStartAt":"2026-10-26T19:30:00"}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "목표 거리는 필수입니다.");
        then(openInviteRoomUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("초대 방 개설: 운영값 범위 밖은 정책이 만든 문구로 400이다")
    void 초대_방_개설은_범위_밖_거리에_정책_문구로_400이다() throws Exception {
        // given
        given(openInviteRoomUsecase.handle(any()))
                .willThrow(new InvalidInviteOptionException("목표 거리는 1000m 이상 42195m 이하여야 합니다."));

        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledStartAt":"2026-10-26T19:30:00","targetDistanceMeters":500}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "목표 거리는 1000m 이상 42195m 이하여야 합니다.");
    }

    @Test
    @DisplayName("초대 방 개설: 고를 수 없는 시각은 409 INVITE_START_AT_OUT_OF_RANGE로 그대로 나간다")
    void 초대_방_개설은_범위_밖_시각에_409다() throws Exception {
        // given -> 노출 목록에서 빠지면 500으로 가려진다
        given(openInviteRoomUsecase.handle(any())).willThrow(new InviteStartAtOutOfRangeException());

        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledStartAt":"2026-10-27T07:30:00","targetDistanceMeters":7000}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result))
                .containsEntry("code", "INVITE_START_AT_OUT_OF_RANGE")
                .containsEntry("message", "선택할 수 없는 시작 시각입니다.");
    }

    @Test
    @DisplayName("친구 초대: 토큰 주체·경로 방 ID·대상 목록을 넘기고 201로 보낸 초대만 돌려준다")
    void 친구_초대는_201로_보낸_초대를_돌려준다() throws Exception {
        // given -> 값이 다르게 바인딩되면 이 스텁이 맞지 않아 빈 응답이 된다
        UUID friend = UuidCreator.getTimeOrderedEpoch();
        given(inviteFriendsUsecase.handle(new InviteFriendsCommand(USER_ID, 130L, List.of(friend))))
                .willReturn(new InviteFriendsResult(List.of(new InviteFriendsResult.SentInvitation(
                        friend, RunningRoomInvitationStatus.PENDING, 1))));

        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/130/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteeIds":["%s"]}
                                """.formatted(friend)))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result)).containsExactly(Map.entry("invitations", List.of(Map.of(
                "userId", friend.toString(), "status", "PENDING", "inviteCount", 1))));
    }

    @Test
    @DisplayName("친구 초대: 대상이 비면 400이고 유스케이스를 부르지 않는다")
    void 친구_초대는_빈_목록에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/130/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteeIds":[]}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "초대할 친구는 1명 이상 50명 이하여야 합니다.");
        then(inviteFriendsUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("친구 초대: 대상이 UUID가 아니면 본문을 읽지 못해 400이다")
    void 친구_초대는_UUID가_아닌_대상에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/130/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteeIds":["not-a-uuid"]}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "MALFORMED_REQUEST_BODY");
        then(inviteFriendsUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("친구 초대: 1 미만의 방 ID는 400이고 유스케이스를 부르지 않는다")
    void 친구_초대는_1_미만의_방_ID에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/0/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteeIds":["%s"]}
                                """.formatted(UuidCreator.getTimeOrderedEpoch())))
                .andReturn();

        // then
        assertInvalidRequest(result);
        then(inviteFriendsUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("친구 초대: 친구가 아닌 사람이 섞이면 409 NOT_FRIEND로 그대로 나간다")
    void 친구_초대는_친구가_아니면_409다() throws Exception {
        // given -> 노출 목록에서 빠지면 500으로 가려진다
        given(inviteFriendsUsecase.handle(any())).willThrow(new NotFriendException());

        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/130/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteeIds":["%s"]}
                                """.formatted(UuidCreator.getTimeOrderedEpoch())))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result))
                .containsEntry("code", "NOT_FRIEND")
                .containsEntry("message", "친구에게만 초대를 보낼 수 있습니다.");
    }

    @Test
    @DisplayName("친구 초대: 방이 없거나 참가자가 아니면 404 NOT_FOUND다")
    void 친구_초대는_방을_못_찾으면_404다() throws Exception {
        // given
        given(inviteFriendsUsecase.handle(any())).willThrow(new InviteRoomNotFoundException());

        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/130/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteeIds":["%s"]}
                                """.formatted(UuidCreator.getTimeOrderedEpoch())))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result)).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("초대 수락: 본문 없이 토큰 주체와 경로 방 ID로 수락하고 201로 방 ID만 돌려준다")
    void 초대_수락은_201로_방_ID를_돌려준다() throws Exception {
        // given
        given(acceptInvitationUsecase.handle(new AcceptInvitationCommand(USER_ID, 130L)))
                .willReturn(new AcceptInvitationResult(130L));

        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/130/invitations/accept")).andReturn();

        // then -> 방 정보는 스트림을 연 직후의 입장 스냅샷이 나른다
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result)).containsExactly(Map.entry("runningRoomId", 130));
    }

    @Test
    @DisplayName("초대 수락: 자리가 차면 409 RUNNING_ROOM_FULL로 그대로 나간다")
    void 초대_수락은_자리가_없으면_409다() throws Exception {
        // given
        given(acceptInvitationUsecase.handle(any())).willThrow(new RunningRoomFullException());

        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/130/invitations/accept")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result))
                .containsEntry("code", "RUNNING_ROOM_FULL")
                .containsEntry("message", "방이 가득 찼습니다.");
    }

    @Test
    @DisplayName("초대 수락: 받은 초대가 없으면 404 NOT_FOUND다")
    void 초대_수락은_초대가_없으면_404다() throws Exception {
        // given -> 방 ID만 알아서는 들어갈 수 없다
        given(acceptInvitationUsecase.handle(any())).willThrow(new InvitationNotFoundException());

        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/130/invitations/accept")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result)).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("초대 수락: 1 미만의 방 ID는 400이고 유스케이스를 부르지 않는다")
    void 초대_수락은_1_미만의_방_ID에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/0/invitations/accept")).andReturn();

        // then
        assertInvalidRequest(result);
        then(acceptInvitationUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("초대 거절: 토큰 주체와 경로 방 ID로 거절하고 204다")
    void 초대_거절은_204다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/130/invitations/decline")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        then(declineInvitationUsecase).should().handle(new DeclineInvitationCommand(USER_ID, 130L));
    }

    @Test
    @DisplayName("초대 거절: 대기 중이 아니면 409 RUNNING_INVITATION_NOT_PENDING으로 그대로 나간다")
    void 초대_거절은_대기_중이_아니면_409다() throws Exception {
        // given
        willThrow(new InvitationNotPendingException()).given(declineInvitationUsecase).handle(any());

        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/130/invitations/decline")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result))
                .containsEntry("code", "RUNNING_INVITATION_NOT_PENDING")
                .containsEntry("message", "이미 응답한 초대입니다.");
    }

    @Test
    @DisplayName("초대 거절: 1 미만의 방 ID는 400이고 유스케이스를 부르지 않는다")
    void 초대_거절은_1_미만의_방_ID에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/running-rooms/-1/invitations/decline")).andReturn();

        // then
        assertInvalidRequest(result);
        then(declineInvitationUsecase).shouldHaveNoInteractions();
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
