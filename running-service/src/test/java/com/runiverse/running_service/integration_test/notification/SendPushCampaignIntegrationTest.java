package com.runiverse.running_service.integration_test.notification;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.device.command.push.SendPushCommand;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.notification.command.campaign.PushCampaignListener;
import com.runiverse.running_service.application.notification.command.campaign.PushCampaignProperties;
import com.runiverse.running_service.application.notification.command.campaign.PushCampaignReverter;
import com.runiverse.running_service.application.notification.command.campaign.PushCampaignSendRequestedEvent;
import com.runiverse.running_service.application.notification.command.campaign.SendPushCampaignCommand;
import com.runiverse.running_service.application.notification.command.campaign.SendPushCampaignHandler;
import com.runiverse.running_service.application.notification.exception.PushCampaignAlreadySentException;
import com.runiverse.running_service.application.notification.exception.PushCampaignNotFoundException;
import com.runiverse.running_service.domain.notification.campaign.vo.PushCampaignStatus;
import com.runiverse.running_service.integration_test.fake.InMemoryPushCampaignStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("공지 푸시 발송 통합 테스트")
public class SendPushCampaignIntegrationTest {

    private static final long CAMPAIGN_ID = 7L;
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final UUID YOUNGHEE = UuidCreator.getTimeOrderedEpoch();

    private final List<PushCampaignSendRequestedEvent> requests = new ArrayList<>();
    private final List<SendPushCommand> sent = new ArrayList<>();
    private InMemoryPushCampaignStore campaignStore;
    private SendPushCampaignHandler handler;
    private PushCampaignListener listener;

    @BeforeEach
    void setUp() {
        campaignStore = new InMemoryPushCampaignStore();
        handler = new SendPushCampaignHandler(
                campaignStore,      // CompletePushCampaignPort
                campaignStore,      // LoadPushCampaignPort
                campaignStore,      // LoadPushCampaignRecipientsPort
                event -> {          // ApplicationEventPublisher — 커밋 뒤 전달은 테스트가 직접 한다
                    if (event instanceof PushCampaignSendRequestedEvent request) {
                        requests.add(request);
                    }
                },
                new PushCampaignProperties(Duration.ofDays(1))
        );
        listener = new PushCampaignListener(
                Runnable::run,                              // 발송 스레드가 할 일을 이 스레드에서 한다
                sent::add,                                  // SendPushUsecase — 받는 사람별 거르기는 SendPushHandler 몫
                new PushCampaignReverter(campaignStore)     // RevertPushCampaignPort
        );
    }

    @Test
    @DisplayName("운영이 넣은 캠페인을 부르면 명단의 모두에게 같은 공지가 한 번 간다")
    void sendsNoticeToEveryRecipientOnce() {
        // given
        campaignStore.insert(CAMPAIGN_ID, "서비스 점검 안내", "11월 20일 02시부터 30분간 점검해요", List.of(CHULSU, YOUNGHEE));

        // when
        handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID));
        deliverAfterCommit();

        // then
        assertThat(campaignStore.statusOf(CAMPAIGN_ID)).isEqualTo(PushCampaignStatus.COMPLETED);
        assertThat(sent).extracting(SendPushCommand::userId).containsExactly(CHULSU, YOUNGHEE);
        assertThat(sent).allSatisfy(command -> {
            assertThat(command.message().type()).isEqualTo(PushType.NOTICE);
            assertThat(command.message().title()).isEqualTo("서비스 점검 안내");
        });
    }

    @Test
    @DisplayName("두 번 부르면 두 번째는 409로 거절하고 다시 보내지 않는다")
    void secondCallIsRejected() {
        // given
        campaignStore.insert(CAMPAIGN_ID, "제목", "본문", List.of(CHULSU));
        handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID));
        deliverAfterCommit();

        // when & then -> 키가 새어도 넣어 둔 캠페인을 한 번 보내는 것 말고는 할 수 없다
        assertThatThrownBy(() -> handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID)))
                .isInstanceOf(PushCampaignAlreadySentException.class);
        deliverAfterCommit();
        assertThat(sent).hasSize(1);
    }

    @Test
    @DisplayName("운영이 DRAFT로 되돌리면 다시 보낸다")
    void resendsAfterResetToDraft() {
        // given -> FCM 전체 장애로 아무도 못 받았다
        campaignStore.insert(CAMPAIGN_ID, "제목", "본문", List.of(CHULSU));
        handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID));
        deliverAfterCommit();

        // when
        campaignStore.resetToDraft(CAMPAIGN_ID);
        handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID));
        deliverAfterCommit();

        // then
        assertThat(sent).extracting(SendPushCommand::userId).containsExactly(CHULSU, CHULSU);
    }

    @Test
    @DisplayName("보내기 전에 서버가 꺼지면 DRAFT로 돌아가 다시 부르면 보낸다")
    void resendsAfterShutdownBeforeStart() {
        // given -> 커밋 직후 서버가 꺼지기 시작해 발송 실행기가 작업을 받지 않았다
        campaignStore.insert(CAMPAIGN_ID, "제목", "본문", List.of(CHULSU));
        PushCampaignListener shuttingDown = new PushCampaignListener(task -> {
            throw new TaskRejectedException("종료 중");
        }, sent::add, new PushCampaignReverter(campaignStore));
        handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID));
        requests.forEach(shuttingDown::send);
        requests.clear();
        assertThat(campaignStore.statusOf(CAMPAIGN_ID)).isEqualTo(PushCampaignStatus.DRAFT);

        // when -> 운영이 새 서버에서 다시 부른다
        handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID));
        deliverAfterCommit();

        // then -> 처음엔 아무도 받지 않아 한 번만 받는다
        assertThat(sent).extracting(SendPushCommand::userId).containsExactly(CHULSU);
        assertThat(campaignStore.statusOf(CAMPAIGN_ID)).isEqualTo(PushCampaignStatus.COMPLETED);
    }

    @Test
    @DisplayName("없는 캠페인은 404로 거절한다")
    void unknownCampaignIsRejected() {
        // when & then
        assertThatThrownBy(() -> handler.handle(new SendPushCampaignCommand(CAMPAIGN_ID)))
                .isInstanceOf(PushCampaignNotFoundException.class);
        assertThat(requests).isEmpty();
    }

    // 커밋 뒤 발송 스레드가 하는 일을 이 스레드에서 한다
    private void deliverAfterCommit() {
        requests.forEach(listener::send);
        requests.clear();
    }
}
