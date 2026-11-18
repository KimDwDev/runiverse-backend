package com.runiverse.running_service.unit_test.notification.presentation;

import com.runiverse.running_service.application.notification.command.campaign.SendPushCampaignCommand;
import com.runiverse.running_service.application.notification.exception.PushCampaignAlreadySentException;
import com.runiverse.running_service.application.notification.exception.PushCampaignNotFoundException;
import com.runiverse.running_service.application.notification.port.in.SendPushCampaignUsecase;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.notification.controller.PushCampaignController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// 관리자 키는 필터가 보므로 여기서는 다루지 않는다(AdminKeyFilterTest)
@ExtendWith(MockitoExtension.class)
@DisplayName("공지 푸시 컨트롤러 단위 테스트")
class PushCampaignControllerTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private SendPushCampaignUsecase sendPushCampaignUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new PushCampaignController(sendPushCampaignUsecase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JSON_MAPPER))
                .build();
    }

    @Test
    @DisplayName("경로의 캠페인 ID를 넘기고 본문 없이 204다")
    void sendsCampaignOfPath() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/admin/push-campaigns/7/send")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        then(sendPushCampaignUsecase).should().handle(new SendPushCampaignCommand(7L));
    }

    @Test
    @DisplayName("이미 보낸 캠페인이면 409 PUSH_CAMPAIGN_ALREADY_SENT다")
    void alreadySentIs409() throws Exception {
        // given
        willThrow(new PushCampaignAlreadySentException())
                .given(sendPushCampaignUsecase).handle(new SendPushCampaignCommand(7L));

        // when
        MvcResult result = mockMvc.perform(post("/admin/push-campaigns/7/send")).andReturn();

        // then -> 노출 목록에 없으면 500으로 가려진다
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "code", "PUSH_CAMPAIGN_ALREADY_SENT",
                "message", "이미 보낸 푸시 캠페인입니다."));
    }

    @Test
    @DisplayName("없는 캠페인이면 공통 404 NOT_FOUND다")
    void notFoundIs404() throws Exception {
        // given
        willThrow(new PushCampaignNotFoundException())
                .given(sendPushCampaignUsecase).handle(new SendPushCampaignCommand(7L));

        // when
        MvcResult result = mockMvc.perform(post("/admin/push-campaigns/7/send")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "code", "NOT_FOUND",
                "message", "요청한 리소스를 찾을 수 없습니다."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    @DisplayName("캠페인 ID가 양의 정수가 아니면 400이고 보내지 않는다")
    void invalidIdIs400(String pushCampaignId) throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/admin/push-campaigns/{id}/send", pushCampaignId)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "INVALID_REQUEST");
        then(sendPushCampaignUsecase).shouldHaveNoInteractions();
    }

    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }
}
