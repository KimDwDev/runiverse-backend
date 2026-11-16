package com.runiverse.running_service.application.campaign.command.send;

import com.runiverse.running_service.application.campaign.exception.PushCampaignAlreadySentException;
import com.runiverse.running_service.application.campaign.exception.PushCampaignNotFoundException;
import com.runiverse.running_service.application.campaign.port.in.SendPushCampaignUsecase;
import com.runiverse.running_service.application.campaign.port.out.CompletePushCampaignPort;
import com.runiverse.running_service.application.campaign.port.out.LoadPushCampaignPort;
import com.runiverse.running_service.application.campaign.port.out.LoadPushCampaignRecipientsPort;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.domain.campaign.PushCampaign;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 운영이 넣어 둔 공지를 한 번만 보낸다 — 상태를 바꾸고 커밋한 뒤 발송 스레드가 한 명씩 보낸다.
// 되돌릴 수 없는 발송이라 상태를 먼저 바꾼다: 보내다 멈춰도 다시 보내지 않는다
@Slf4j
@Service
@RequiredArgsConstructor
public class SendPushCampaignHandler implements SendPushCampaignUsecase {

    private final CompletePushCampaignPort completePushCampaignPort;
    private final LoadPushCampaignPort loadPushCampaignPort;
    private final LoadPushCampaignRecipientsPort loadPushCampaignRecipientsPort;
    private final ApplicationEventPublisher eventPublisher;
    private final PushCampaignProperties pushCampaignProperties;

    @Override
    @Transactional
    public void handle(SendPushCampaignCommand command) {
        long pushCampaignId = command.pushCampaignId();
        // 1. 보내기 전일 때만 바꾼다 — 같은 캠페인을 동시에 불러도 하나만 이긴다
        if (!completePushCampaignPort.complete(pushCampaignId)) {
            // 상태는 DRAFT로 돌아가지 않으니(운영이 SQL로 되돌릴 때만) 다시 읽어도 판정이 뒤집히지 않는다
            if (loadPushCampaignPort.load(pushCampaignId).isEmpty()) {
                log.info("[캠페인] 공지 발송 실패: 없는 캠페인 - pushCampaignId={}", pushCampaignId);
                throw new PushCampaignNotFoundException();
            }
            log.info("[캠페인] 공지 발송 실패: 이미 보낸 캠페인 - pushCampaignId={}", pushCampaignId);
            throw new PushCampaignAlreadySentException();
        }
        // 2. 문구와 명단을 이 트랜잭션에서 읽는다 — 공백 문구면 도메인이 거절해 상태 변경도 롤백된다
        PushCampaign campaign = loadPushCampaignPort.load(pushCampaignId)
                .orElseThrow(() -> new IllegalStateException("방금 바꾼 캠페인이 없다"));
        List<UUID> recipientIds = loadPushCampaignRecipientsPort.loadRecipientIds(pushCampaignId).stream()
                .map(UserId::value)
                .toList();
        // 3. 커밋 뒤에 보낸다. 갈 화면이 없어 data는 비운다
        LocalDateTime expiresAt = LocalDateTime.now().plus(pushCampaignProperties.messageTtl());
        PushMessage message = new PushMessage(PushType.NOTICE, campaign.getTitle(), campaign.getBody(),
                Map.of(), expiresAt);
        eventPublisher.publishEvent(new PushCampaignSendRequestedEvent(pushCampaignId, message, recipientIds));
        // 이 줄 뒤에 발송 시작 줄이 없으면 대기 줄에서 서버 종료로 버려진 캠페인이다
        log.info("[캠페인] 공지 발송 요청 성공 - pushCampaignId={}, recipientCount={}",
                pushCampaignId, recipientIds.size());
    }
}
