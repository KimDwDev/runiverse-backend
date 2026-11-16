package com.runiverse.running_service.unit_test.campaign.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.campaign.command.send.PushCampaignListener;
import com.runiverse.running_service.application.campaign.command.send.PushCampaignSendRequestedEvent;
import com.runiverse.running_service.application.device.command.push.SendPushCommand;
import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("공지 푸시 발송 리스너 단위 테스트")
class PushCampaignListenerTest {

    private static final long CAMPAIGN_ID = 7L;
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final UUID YOUNGHEE = UuidCreator.getTimeOrderedEpoch();
    private static final UUID MINSU = UuidCreator.getTimeOrderedEpoch();
    private static final PushMessage MESSAGE = new PushMessage(PushType.NOTICE, "서비스 점검 안내",
            "11월 20일 02시부터 30분간 점검해요", Map.of(), LocalDateTime.of(2026, 11, 17, 10, 0));

    @Mock
    private SendPushUsecase sendPushUsecase;

    private PushCampaignListener listener;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        listener = new PushCampaignListener(sendPushUsecase);
        log = LogCapture.of(PushCampaignListener.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
        // 끊김 표시를 남기면 같은 스레드의 다음 테스트가 시작부터 멈춘다
        Thread.interrupted();
    }

    @Test
    @DisplayName("명단 순서대로 한 명씩 같은 문구를 보낸다")
    void sendsToEachRecipient() {
        // when
        listener.send(event(CHULSU, YOUNGHEE, MINSU));

        // then
        InOrder order = inOrder(sendPushUsecase);
        order.verify(sendPushUsecase).handle(new SendPushCommand(CHULSU, MESSAGE));
        order.verify(sendPushUsecase).handle(new SendPushCommand(YOUNGHEE, MESSAGE));
        order.verify(sendPushUsecase).handle(new SendPushCommand(MINSU, MESSAGE));
        assertThat(log.messages(Level.INFO)).containsExactly(
                "[캠페인] 공지 발송 시작 - pushCampaignId=7, recipientCount=3",
                "[캠페인] 공지 발송 성공 - pushCampaignId=7, recipientCount=3, exceptionCount=0");
    }

    @Test
    @DisplayName("한 명이 예외로 실패해도 뒤의 사람에게 이어서 보내고 예외 수를 남긴다")
    void continuesAfterFailure() {
        // given -> 영희 차례에 Redis가 잠깐 끊겼다
        willAnswer(invocation -> {
            if (invocation.<SendPushCommand>getArgument(0).userId().equals(YOUNGHEE)) {
                throw new IllegalStateException("Redis 연결 끊김");
            }
            return null;
        }).given(sendPushUsecase).handle(any());

        // when
        listener.send(event(CHULSU, YOUNGHEE, MINSU));

        // then
        verify(sendPushUsecase).handle(new SendPushCommand(MINSU, MESSAGE));
        assertThat(log.messages(Level.ERROR)).singleElement().asString()
                .isEqualTo("[캠페인] 공지 발송 실패: 처리하지 못한 예외 - pushCampaignId=7, targetUserId=" + YOUNGHEE);
        assertThat(log.messages(Level.INFO)).last().asString()
                .isEqualTo("[캠페인] 공지 발송 성공 - pushCampaignId=7, recipientCount=3, exceptionCount=1");
    }

    @Test
    @DisplayName("서버가 꺼지며 스레드가 끊기면 남은 사람에게 보내지 않고 남은 인원을 남긴다")
    void stopsWhenInterrupted() {
        // given -> 철수에게 보내는 사이 실행기가 종료되며 스레드를 끊었다
        willAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return null;
        }).given(sendPushUsecase).handle(new SendPushCommand(CHULSU, MESSAGE));

        // when
        listener.send(event(CHULSU, YOUNGHEE, MINSU));

        // then -> 다시 보내려면 운영이 DRAFT로 되돌려야 해서, 몇 명이 남았는지가 판단 근거다
        verify(sendPushUsecase).handle(new SendPushCommand(CHULSU, MESSAGE));
        verify(sendPushUsecase, never()).handle(new SendPushCommand(YOUNGHEE, MESSAGE));
        verify(sendPushUsecase, never()).handle(new SendPushCommand(MINSU, MESSAGE));
        assertThat(log.messages(Level.WARN)).singleElement().asString()
                .isEqualTo("[캠페인] 공지 발송 건너뜀: 종료 중 - pushCampaignId=7, remainingCount=2");
        assertThat(log.messages(Level.INFO)).noneMatch(message -> message.contains("공지 발송 성공"));
    }

    @Test
    @DisplayName("받는 사람이 없으면 아무에게도 보내지 않고 시작·성공만 남긴다")
    void emptyRecipients() {
        // when
        listener.send(event());

        // then
        verify(sendPushUsecase, never()).handle(any());
        assertThat(log.messages(Level.INFO)).containsExactly(
                "[캠페인] 공지 발송 시작 - pushCampaignId=7, recipientCount=0",
                "[캠페인] 공지 발송 성공 - pushCampaignId=7, recipientCount=0, exceptionCount=0");
    }

    private static PushCampaignSendRequestedEvent event(UUID... recipientIds) {
        return new PushCampaignSendRequestedEvent(CAMPAIGN_ID, MESSAGE, List.of(recipientIds));
    }
}
