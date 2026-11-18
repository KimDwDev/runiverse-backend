package com.runiverse.running_service.unit_test.notification.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.notification.command.campaign.PushCampaignReverter;
import com.runiverse.running_service.application.notification.command.campaign.PushCampaignSendRequestedEvent;
import com.runiverse.running_service.application.notification.command.campaign.PushCampaignSendTask;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("공지 푸시 발송 작업 단위 테스트")
class PushCampaignSendTaskTest {

    private static final long CAMPAIGN_ID = 7L;
    private static final PushMessage MESSAGE = new PushMessage(PushType.NOTICE, "서비스 점검 안내",
            "11월 20일 02시부터 30분간 점검해요", Map.of(), LocalDateTime.of(2026, 11, 17, 10, 0));

    @Mock
    private SendPushUsecase sendPushUsecase;

    @Mock
    private PushCampaignReverter pushCampaignReverter;

    private PushCampaignSendTask task;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        PushCampaignSendRequestedEvent event = new PushCampaignSendRequestedEvent(CAMPAIGN_ID, MESSAGE,
                List.of(UuidCreator.getTimeOrderedEpoch()));
        task = new PushCampaignSendTask(event, sendPushUsecase, pushCampaignReverter);
        log = LogCapture.of(PushCampaignSendTask.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("시작 전에 취소된 작업은 나중에 실행돼도 보내지 않는다 — 되돌린 캠페인이 뒤늦게 나가지 않는다")
    void cancelledTaskNeverSends() {
        // given
        given(pushCampaignReverter.revert(CAMPAIGN_ID)).willReturn(true);
        task.cancel(false);

        // when -> 취소와 실행 시작이 엇갈려 스레드가 늦게 꺼냈다
        task.run();

        // then
        verify(sendPushUsecase, never()).handle(any());
        verify(pushCampaignReverter).revert(CAMPAIGN_ID);
    }

    @Test
    @DisplayName("여러 번 취소돼도 한 번만 되돌린다")
    void revertsOnceOnRepeatedCancel() {
        // given
        given(pushCampaignReverter.revert(CAMPAIGN_ID)).willReturn(true);

        // when
        task.cancel(false);
        task.cancel(true);

        // then
        verify(pushCampaignReverter).revert(CAMPAIGN_ID);
    }

    @Test
    @DisplayName("되돌리다 DB 오류가 나도 취소한 쪽으로 던지지 않고 캠페인과 함께 ERROR로 남긴다")
    void revertFailureDoesNotPropagate() {
        // given -> 종료 때 대기 줄의 작업을 차례로 취소하는 중이다. 던지면 뒤의 캠페인이 되돌려지지 않는다
        given(pushCampaignReverter.revert(CAMPAIGN_ID)).willThrow(new IllegalStateException("DB 연결 끊김"));

        // when & then
        assertThatCode(() -> task.cancel(false)).doesNotThrowAnyException();
        assertThat(log.messages(Level.ERROR)).singleElement().asString()
                .isEqualTo("[알림] 공지 상태 되돌림 실패: 처리하지 못한 예외 - pushCampaignId=7");
    }

    @Test
    @DisplayName("되돌릴 행이 없으면 보낸 상태가 아니었다고 남긴다")
    void warnsWhenNothingToRevert() {
        // given -> 그사이 운영이 SQL로 상태를 고쳤다
        given(pushCampaignReverter.revert(CAMPAIGN_ID)).willReturn(false);

        // when
        task.cancel(false);

        // then
        assertThat(log.messages(Level.WARN)).containsExactly(
                "[알림] 공지 발송 건너뜀: 시작 전 종료 - pushCampaignId=7",
                "[알림] 공지 상태 되돌림 건너뜀: 보낸 상태 아님 - pushCampaignId=7");
    }
}
