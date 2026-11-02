package com.runiverse.running_service.unit_test.legacy.v1_1.match;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchCommand;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchResult;
import com.runiverse.running_service.application.match.command.cancel.CancelMatchCommand;
import com.runiverse.running_service.application.match.port.in.ApplyMatchUsecase;
import com.runiverse.running_service.application.match.port.in.CancelMatchUsecase;
import com.runiverse.running_service.application.match.port.in.GetMatchSlotsUsecase;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsQuery;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult;
import com.runiverse.running_service.infrastructure.config.JacksonConfig;
import com.runiverse.running_service.legacy.v1_1.match.controller.LegacyRunningMatchController;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.match.request.ApplyMatchRequest;
import com.runiverse.running_service.presentation.match.response.ApplyMatchResponse;
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
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// 1.1.x 앱이 쓰던 /running-matches 경로·형식이 1.1.1 서버와 같은지 본다.
// 동작은 1.2.0 유스케이스에 맡기므로 유스케이스로 넘기는 값과 와이어 모양만 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("1.1.x 매칭 컨트롤러 단위 테스트")
class LegacyRunningMatchControllerTest {

    // UserId VO가 UUIDv7만 받는다
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new SimpleModule()
                    .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(JacksonConfig.SECONDS)))
            .build();

    @Mock
    private GetMatchSlotsUsecase getMatchSlotsUsecase;

    @Mock
    private ApplyMatchUsecase applyMatchUsecase;

    @Mock
    private CancelMatchUsecase cancelMatchUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new LegacyRunningMatchController(
                getMatchSlotsUsecase, applyMatchUsecase, cancelMatchUsecase))
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
    @DisplayName("슬롯 조회: 요청한 거리의 슬롯만 slots로 내린다")
    void slotsOfRequestedDistanceOnly() throws Exception {
        // given
        LocalDate date = LocalDate.now().plusDays(1);
        given(getMatchSlotsUsecase.handle(any())).willReturn(twoDistances(date));

        // when
        MvcResult result = mockMvc.perform(get("/running-matches/slots")
                        .param("date", date.toString())
                        .param("targetDistanceMeters", "5000"))
                .andReturn();

        // then -> 1.1.1 서버와 같은 { slots: [...] } — 거리 묶음이 없다
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        verify(getMatchSlotsUsecase).handle(new GetMatchSlotsQuery(date));
        Map<String, Object> body = body(result);
        assertThat(body.keySet()).containsExactly("slots");
        assertThat(body.get("slots")).isEqualTo(List.of(
                Map.of("scheduledStartAt", date + "T18:00:00", "waitingCount", 0, "selectable", false),
                Map.of("scheduledStartAt", date + "T18:30:00", "waitingCount", 3, "selectable", true)));
    }

    @Test
    @DisplayName("슬롯 조회: 날짜를 생략하면 오늘을 조회한다")
    void slotsDefaultToToday() throws Exception {
        // given
        given(getMatchSlotsUsecase.handle(any())).willReturn(twoDistances(LocalDate.now()));

        // when
        mockMvc.perform(get("/running-matches/slots").param("targetDistanceMeters", "3000"));

        // then
        verify(getMatchSlotsUsecase).handle(new GetMatchSlotsQuery(LocalDate.now()));
    }

    @Test
    @DisplayName("슬롯 조회: 선택지 밖 거리는 1.1.1 서버처럼 400과 선택지 문구다")
    void unknownDistanceIsInvalidOption() throws Exception {
        // given
        given(getMatchSlotsUsecase.handle(any())).willReturn(twoDistances(LocalDate.now()));

        // when
        MvcResult result = mockMvc.perform(get("/running-matches/slots").param("targetDistanceMeters", "7000"))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "목표 거리는 3000, 5000 중 하나여야 합니다.");
    }

    @Test
    @DisplayName("슬롯 조회: 거리를 빠뜨리면 조회 전에 400이다")
    void missingDistanceIsRejected() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/running-matches/slots")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "목표 거리는 필수입니다.");
        verifyNoInteractions(getMatchSlotsUsecase);
    }

    @Test
    @DisplayName("슬롯 조회: 거리 형식이 틀리면 조회 전에 공통 문구로 400이다")
    void malformedDistanceIsRejected() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/running-matches/slots").param("targetDistanceMeters", "abc"))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
        verifyNoInteractions(getMatchSlotsUsecase);
    }

    @Test
    @DisplayName("신청: POST /running-matches 본문을 그대로 넘기고 201로 방 ID를 돌려준다")
    void applyReturnsCreatedRoom() throws Exception {
        // given -> 값이 다르게 바인딩되면 이 스텁이 맞지 않아 빈 응답이 된다
        given(applyMatchUsecase.handle(new ApplyMatchCommand(
                USER_ID, LocalDateTime.of(2026, 11, 3, 19, 0), 5000)))
                .willReturn(new ApplyMatchResult(42L));

        // when
        MvcResult result = mockMvc.perform(post("/running-matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledStartAt":"2026-11-03T19:00:00","targetDistanceMeters":5000}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result)).isEqualTo(Map.of("runningRoomId", 42));
    }

    @Test
    @DisplayName("신청: 함께 쓰는 1.2.0 DTO의 필드가 1.1.1 서버와 같다")
    void applyWireShapeIsFrozen() {
        // when & then -> 1.2.x에서 이 DTO를 바꾸면 1.1.x 앱도 함께 깨진다 — 그때는 legacy 사본으로 떼어 낸다
        assertThat(componentNames(ApplyMatchRequest.class))
                .containsExactly("scheduledStartAt", "targetDistanceMeters");
        assertThat(componentNames(ApplyMatchResponse.class)).containsExactly("runningRoomId");
    }

    @Test
    @DisplayName("취소: 본문 없이 DELETE /running-matches를 받아 토큰 주체로 204다")
    void cancelIsDeleteWithNoContent() throws Exception {
        // when
        MvcResult result = mockMvc.perform(delete("/running-matches")).andReturn();

        // then -> 1.2.0의 POST /match-rooms/leave와 같은 유스케이스다
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        verify(cancelMatchUsecase).handle(new CancelMatchCommand(USER_ID));
    }

    private static GetMatchSlotsResult twoDistances(LocalDate date) {
        return new GetMatchSlotsResult(List.of(
                new GetMatchSlotsResult.DistanceSlots(3_000, List.of(
                        new GetMatchSlotsResult.MatchSlot(date.atTime(18, 0), 1, false))),
                new GetMatchSlotsResult.DistanceSlots(5_000, List.of(
                        new GetMatchSlotsResult.MatchSlot(date.atTime(18, 0), 0, false),
                        new GetMatchSlotsResult.MatchSlot(date.atTime(18, 30), 3, true)))));
    }

    private static List<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }
}
