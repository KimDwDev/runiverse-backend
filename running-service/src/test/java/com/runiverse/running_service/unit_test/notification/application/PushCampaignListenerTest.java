package com.runiverse.running_service.unit_test.notification.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.device.command.push.SendPushCommand;
import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.notification.command.campaign.PushCampaignListener;
import com.runiverse.running_service.application.notification.command.campaign.PushCampaignReverter;
import com.runiverse.running_service.application.notification.command.campaign.PushCampaignSendRequestedEvent;
import com.runiverse.running_service.application.notification.command.campaign.PushCampaignSendTask;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
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

    @Mock
    private PushCampaignReverter pushCampaignReverter;

    private PushCampaignListener listener;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        // 발송 스레드가 할 일을 이 스레드에서 한다
        listener = new PushCampaignListener(Runnable::run, sendPushUsecase, pushCampaignReverter);
        log = LogCapture.of(PushCampaignSendTask.class);
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
                "[알림] 공지 발송 시작 - pushCampaignId=7, recipientCount=3",
                "[알림] 공지 발송 성공 - pushCampaignId=7, recipientCount=3, exceptionCount=0");
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
                .isEqualTo("[알림] 공지 발송 실패: 처리하지 못한 예외 - pushCampaignId=7, targetUserId=" + YOUNGHEE);
        assertThat(log.messages(Level.INFO)).last().asString()
                .isEqualTo("[알림] 공지 발송 성공 - pushCampaignId=7, recipientCount=3, exceptionCount=1");
    }

    @Test
    @DisplayName("서버가 꺼지며 스레드가 끊기면 남은 사람에게 보내지 않고 남은 인원을 남기며, 상태는 되돌리지 않는다")
    void stopsWhenInterrupted() {
        // given -> 철수에게 보내는 사이 실행기가 종료되며 스레드를 끊었다
        willAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return null;
        }).given(sendPushUsecase).handle(new SendPushCommand(CHULSU, MESSAGE));

        // when
        listener.send(event(CHULSU, YOUNGHEE, MINSU));

        // then -> 철수는 받았을 수 있어 DRAFT로 되돌리면 다시 부를 때 두 번 받는다. 몇 명이 남았는지가 운영의 판단 근거다
        verify(sendPushUsecase).handle(new SendPushCommand(CHULSU, MESSAGE));
        verify(sendPushUsecase, never()).handle(new SendPushCommand(YOUNGHEE, MESSAGE));
        verify(sendPushUsecase, never()).handle(new SendPushCommand(MINSU, MESSAGE));
        verify(pushCampaignReverter, never()).revert(anyLong());
        assertThat(log.messages(Level.WARN)).singleElement().asString()
                .isEqualTo("[알림] 공지 발송 건너뜀: 종료 중 - pushCampaignId=7, remainingCount=2");
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
                "[알림] 공지 발송 시작 - pushCampaignId=7, recipientCount=0",
                "[알림] 공지 발송 성공 - pushCampaignId=7, recipientCount=0, exceptionCount=0");
    }

    @Test
    @DisplayName("서버가 꺼지는 중이라 실행기가 받지 않으면 아무에게도 보내지 않고 DRAFT로 되돌린다")
    void revertsWhenExecutorRejects() {
        // given -> 종료가 시작된 실행기는 커밋 뒤 넘긴 작업을 거절한다
        listener = new PushCampaignListener(task -> {
            throw new TaskRejectedException("종료 중");
        }, sendPushUsecase, pushCampaignReverter);
        given(pushCampaignReverter.revert(CAMPAIGN_ID)).willReturn(true);

        // when
        listener.send(event(CHULSU, YOUNGHEE));

        // then -> 받은 사람이 없으니 운영이 다시 부르면 처음부터 보낸다
        verify(sendPushUsecase, never()).handle(any());
        verify(pushCampaignReverter).revert(CAMPAIGN_ID);
        assertThat(log.messages(Level.WARN)).containsExactly("[알림] 공지 발송 건너뜀: 시작 전 종료 - pushCampaignId=7");
        assertThat(log.messages(Level.INFO)).containsExactly("[알림] 공지 상태 되돌림 성공 - pushCampaignId=7");
    }

    @Test
    @DisplayName("사람별로 잡지 못한 오류가 발송 밖으로 나오면 캠페인과 함께 ERROR로 남기고 상태는 되돌리지 않는다")
    void logsFailureOutsideRecipientLoop() {
        // given -> 사람별 처리는 RuntimeException만 잡는다
        willThrow(new StackOverflowError("깊은 재귀")).given(sendPushUsecase).handle(new SendPushCommand(CHULSU, MESSAGE));

        // when
        listener.send(event(CHULSU, YOUNGHEE));

        // then -> 발송 작업이 품은 오류는 꺼내지 않으면 아무 데도 남지 않는다
        verify(pushCampaignReverter, never()).revert(anyLong());
        assertThat(log.messages(Level.ERROR)).singleElement().asString()
                .isEqualTo("[알림] 공지 발송 실패: 처리하지 못한 예외 - pushCampaignId=7");
    }

    private static PushCampaignSendRequestedEvent event(UUID... recipientIds) {
        return new PushCampaignSendRequestedEvent(CAMPAIGN_ID, MESSAGE, List.of(recipientIds));
    }
}
