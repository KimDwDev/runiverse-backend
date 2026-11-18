package com.runiverse.running_service.application.campaign.command.send;

import com.runiverse.running_service.application.device.command.push.SendPushCommand;
import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

// 캠페인 하나를 받는 사람마다 보내는 작업. 시작하기 전에 취소되면 받은 사람이 없으니 DRAFT로 되돌린다.
// 취소는 실행기가 받지 않았거나 종료 때 대기 줄에서 꺼낸 작업에만 부른다 — 보내는 중인 작업을 취소하면
// 발송이 이어지는 동안 DRAFT가 돼, 그때 다시 부르면 두 번 간다.
// 받는 사람별 결과(건너뜀·성공·실패)는 발송 유스케이스가 [기기]로 남긴다 — 이 캠페인의 줄은 스레드 이름으로 모아 본다
@Slf4j
public class PushCampaignSendTask extends FutureTask<Void> {

    private final long pushCampaignId;
    private final PushCampaignReverter pushCampaignReverter;

    public PushCampaignSendTask(PushCampaignSendRequestedEvent event, SendPushUsecase sendPushUsecase,
                                PushCampaignReverter pushCampaignReverter) {
        super(() -> {
            sendToRecipients(event, sendPushUsecase);
            return null;
        });
        this.pushCampaignId = event.pushCampaignId();
        this.pushCampaignReverter = pushCampaignReverter;
    }

    // 취소된 작업은 본문이 돌지 않는다 — 되돌린 캠페인이 뒤늦게 나가는 일이 없다
    @Override
    protected void done() {
        if (isCancelled()) {
            revertBeforeStart();
            return;
        }
        logUnhandledFailure();
    }

    // 트랜잭션은 열지 않는다 — 발송 유스케이스가 DB를 쓰는 동안만 짧게 연다
    private static void sendToRecipients(PushCampaignSendRequestedEvent event, SendPushUsecase sendPushUsecase) {
        long pushCampaignId = event.pushCampaignId();
        List<UUID> recipientIds = event.recipientIds();
        log.info("[캠페인] 공지 발송 시작 - pushCampaignId={}, recipientCount={}", pushCampaignId, recipientIds.size());
        // 예외로 끝난 사람만 센다 — FCM이 받지 않은 것처럼 정상으로 끝난 실패는 발송 유스케이스의 [기기] 줄로 본다
        int exceptionCount = 0;
        for (int index = 0; index < recipientIds.size(); index++) {
            // 서버가 꺼지며 실행기가 끊었다 — 남은 사람은 받지 못한다. 일부가 받았을 수 있어 되돌리지 않고 운영이 판단한다
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

    // 종료 때는 대기 줄의 작업을 차례로 취소하는 스레드에서 돈다 — 여기서 던지면 뒤의 캠페인이 되돌려지지 않는다
    private void revertBeforeStart() {
        log.warn("[캠페인] 공지 발송 건너뜀: 시작 전 종료 - pushCampaignId={}", pushCampaignId);
        try {
            // 성공 줄은 되돌림 트랜잭션이 커밋된 뒤에 남긴다
            if (pushCampaignReverter.revert(pushCampaignId)) {
                log.info("[캠페인] 공지 상태 되돌림 성공 - pushCampaignId={}", pushCampaignId);
            } else {
                log.warn("[캠페인] 공지 상태 되돌림 건너뜀: 보낸 상태 아님 - pushCampaignId={}", pushCampaignId);
            }
        } catch (RuntimeException e) {
            log.error("[캠페인] 공지 상태 되돌림 실패: 처리하지 못한 예외 - pushCampaignId={}", pushCampaignId, e);
        }
    }

    // 본문 밖으로 나온 예외는 이 작업이 품고 아무도 꺼내지 않는다 — 여기서 남긴다. 일부가 받았을 수 있어 되돌리지 않는다
    private void logUnhandledFailure() {
        try {
            get();
        } catch (ExecutionException e) {
            log.error("[캠페인] 공지 발송 실패: 처리하지 못한 예외 - pushCampaignId={}", pushCampaignId, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
