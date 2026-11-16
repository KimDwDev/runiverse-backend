package com.runiverse.running_service.application.campaign.command.send;

import com.runiverse.running_service.application.device.command.push.SendPushCommand;
import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

// 받는 사람별 결과(건너뜀·성공·실패)는 발송 유스케이스가 [기기]로 남긴다 — 이 캠페인의 줄은 스레드 이름으로 모아 본다
@Slf4j
@Component
@RequiredArgsConstructor
public class PushCampaignListener {

    private final SendPushUsecase sendPushUsecase;

    // 한 명씩 FCM을 기다리므로 요청 스레드와 다른 푸시의 발송 스레드를 쓰지 않는다.
    // 트랜잭션은 열지 않는다 — 발송 유스케이스가 DB를 쓰는 동안만 짧게 연다
    @Async("pushCampaignExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(PushCampaignSendRequestedEvent event) {
        long pushCampaignId = event.pushCampaignId();
        List<UUID> recipientIds = event.recipientIds();
        log.info("[캠페인] 공지 발송 시작 - pushCampaignId={}, recipientCount={}", pushCampaignId, recipientIds.size());
        // 예외로 끝난 사람만 센다 — FCM이 받지 않은 것처럼 정상으로 끝난 실패는 발송 유스케이스의 [기기] 줄로 본다
        int exceptionCount = 0;
        for (int index = 0; index < recipientIds.size(); index++) {
            // 서버가 꺼지며 실행기가 끊었다 — 남은 사람은 받지 못한다. 다시 보내려면 운영이 DRAFT로 되돌린다
            if (Thread.currentThread().isInterrupted()) {
                log.warn("[캠페인] 공지 발송 건너뜀: 종료 중 - pushCampaignId={}, remainingCount={}",
                        pushCampaignId, recipientIds.size() - index);
                return;
            }
            UUID userId = recipientIds.get(index);
            // 한 사람의 DB·Redis 오류로 뒤의 사람 전원이 못 받지 않게 그 사람만 넘긴다
            try {
                sendPushUsecase.handle(new SendPushCommand(userId, event.message()));
            } catch (RuntimeException e) {
                exceptionCount++;
                log.error("[캠페인] 공지 발송 실패: 처리하지 못한 예외 - pushCampaignId={}, targetUserId={}",
                        pushCampaignId, userId, e);
            }
        }
        log.info("[캠페인] 공지 발송 성공 - pushCampaignId={}, recipientCount={}, exceptionCount={}",
                pushCampaignId, recipientIds.size(), exceptionCount);
    }
}
