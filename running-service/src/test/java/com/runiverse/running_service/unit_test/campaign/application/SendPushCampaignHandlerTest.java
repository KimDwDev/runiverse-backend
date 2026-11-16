package com.runiverse.running_service.unit_test.campaign.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.campaign.command.send.PushCampaignProperties;
import com.runiverse.running_service.application.campaign.command.send.PushCampaignSendRequestedEvent;
import com.runiverse.running_service.application.campaign.command.send.SendPushCampaignCommand;
import com.runiverse.running_service.application.campaign.command.send.SendPushCampaignHandler;
import com.runiverse.running_service.application.campaign.exception.PushCampaignAlreadySentException;
import com.runiverse.running_service.application.campaign.exception.PushCampaignNotFoundException;
import com.runiverse.running_service.application.campaign.port.out.CompletePushCampaignPort;
import com.runiverse.running_service.application.campaign.port.out.LoadPushCampaignPort;
import com.runiverse.running_service.application.campaign.port.out.LoadPushCampaignRecipientsPort;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.domain.campaign.PushCampaign;
import com.runiverse.running_service.domain.campaign.exception.PushCampaignTitleRequiredException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("공지 푸시 발송 단위 테스트")
class SendPushCampaignHandlerTest {

    private static final long CAMPAIGN_ID = 7L;
    private static final Duration MESSAGE_TTL = Duration.ofDays(1);
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final UUID YOUNGHEE = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private CompletePushCampaignPort completePushCampaignPort;

    @Mock
    private LoadPushCampaignPort loadPushCampaignPort;

    @Mock
    private LoadPushCampaignRecipientsPort loadPushCampaignRecipientsPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SendPushCampaignHandler handler;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        handler = new SendPushCampaignHandler(completePushCampaignPort, loadPushCampaignPort,
                loadPushCampaignRecipientsPort, eventPublisher, new PushCampaignProperties(MESSAGE_TTL));
        log = LogCapture.of(SendPushCampaignHandler.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("보내기 전 캠페인이면 상태를 바꾸고 문구·명단을 실어 커밋 뒤 발송을 요청한다")
    void requestsSendAfterCompleting() {
        // given
        given(completePushCampaignPort.complete(CAMPAIGN_ID)).willReturn(true);
        given(loadPushCampaignPort.load(CAMPAIGN_ID))
                .willReturn(Optional.of(new PushCampaign("서비스 점검 안내", "11월 20일 02시부터 30분간 점검해요")));
        given(loadPushCampaignRecipientsPort.loadRecipientIds(CAMPAIGN_ID))
                .willReturn(List.of(new UserId(CHULSU), new UserId(YOUNGHEE)));

        // when
        handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID));

        // then -> 갈 화면이 없어 data는 비고, 만료는 지금부터 하루 뒤다
        ArgumentCaptor<PushCampaignSendRequestedEvent> captor =
                ArgumentCaptor.forClass(PushCampaignSendRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        PushCampaignSendRequestedEvent event = captor.getValue();
        assertThat(event.pushCampaignId()).isEqualTo(CAMPAIGN_ID);
        assertThat(event.recipientIds()).containsExactly(CHULSU, YOUNGHEE);
        PushMessage message = event.message();
        assertThat(message.type()).isEqualTo(PushType.NOTICE);
        assertThat(message.title()).isEqualTo("서비스 점검 안내");
        assertThat(message.body()).isEqualTo("11월 20일 02시부터 30분간 점검해요");
        assertThat(message.data()).isEmpty();
        assertThat(message.expiresAt()).isCloseTo(LocalDateTime.now().plus(MESSAGE_TTL), within(Duration.ofSeconds(5)));
        // 발송 시작 줄과 짝을 맞춰, 대기 줄에서 서버 종료로 버려진 캠페인을 찾는다
        assertThat(log.messages(Level.INFO))
                .containsExactly("[캠페인] 공지 발송 요청 성공 - pushCampaignId=7, recipientCount=2");
    }

    @Test
    @DisplayName("받는 사람이 없어도 상태를 바꾸고 빈 명단으로 요청한다")
    void requestsWithEmptyRecipients() {
        // given -> 조건에 맞는 사람이 없던 캠페인도 한 번 부르면 끝난 것으로 본다
        given(completePushCampaignPort.complete(CAMPAIGN_ID)).willReturn(true);
        given(loadPushCampaignPort.load(CAMPAIGN_ID)).willReturn(Optional.of(new PushCampaign("제목", "본문")));
        given(loadPushCampaignRecipientsPort.loadRecipientIds(CAMPAIGN_ID)).willReturn(List.of());

        // when
        handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID));

        // then
        ArgumentCaptor<PushCampaignSendRequestedEvent> captor =
                ArgumentCaptor.forClass(PushCampaignSendRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().recipientIds()).isEmpty();
    }

    @Test
    @DisplayName("이미 보낸 캠페인이면 409 예외이고 다시 보내지 않는다")
    void alreadySentFails() {
        // given -> 상태를 바꾸지 못했는데 캠페인은 있다
        given(completePushCampaignPort.complete(CAMPAIGN_ID)).willReturn(false);
        given(loadPushCampaignPort.load(CAMPAIGN_ID)).willReturn(Optional.of(new PushCampaign("제목", "본문")));

        // when & then
        assertThatThrownBy(() -> handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID)))
                .isInstanceOf(PushCampaignAlreadySentException.class)
                .hasMessage("이미 보낸 푸시 캠페인입니다.");
        verify(loadPushCampaignRecipientsPort, never()).loadRecipientIds(CAMPAIGN_ID);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("없는 캠페인이면 404 예외이고 보내지 않는다")
    void notFoundFails() {
        // given
        given(completePushCampaignPort.complete(CAMPAIGN_ID)).willReturn(false);
        given(loadPushCampaignPort.load(CAMPAIGN_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID)))
                .isInstanceOf(PushCampaignNotFoundException.class)
                .hasMessage("요청한 리소스를 찾을 수 없습니다.");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("문구가 공백뿐이면 도메인 예외로 멈추고 발송을 요청하지 않는다")
    void blankTextStopsBeforeRequest() {
        // given -> 어댑터가 행을 도메인으로 바꾸다 거절한다. 예외로 트랜잭션이 롤백돼 캠페인은 DRAFT로 남는다
        given(completePushCampaignPort.complete(CAMPAIGN_ID)).willReturn(true);
        given(loadPushCampaignPort.load(CAMPAIGN_ID)).willThrow(new PushCampaignTitleRequiredException());

        // when & then
        assertThatThrownBy(() -> handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID)))
                .isInstanceOf(PushCampaignTitleRequiredException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
