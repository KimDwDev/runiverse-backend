package com.runiverse.running_service.application.notification.command.campaign;

import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PushCampaignListener {

    private final TaskExecutor pushCampaignExecutor;
    private final SendPushUsecase sendPushUsecase;
    private final PushCampaignReverter pushCampaignReverter;

    public PushCampaignListener(
            @Qualifier("pushCampaignExecutor") TaskExecutor pushCampaignExecutor,
            SendPushUsecase sendPushUsecase,
            PushCampaignReverter pushCampaignReverter
    ) {
        this.pushCampaignExecutor = pushCampaignExecutor;
        this.sendPushUsecase = sendPushUsecase;
        this.pushCampaignReverter = pushCampaignReverter;
    }

    // 한 명씩 FCM을 기다리므로 요청 스레드와 다른 푸시의 발송 스레드를 쓰지 않고 전용 실행기에 넘긴다.
    // @Async 대신 직접 넘겨야 실행기가 받지 않은 작업을 알 수 있다 — 대기 줄이 무제한이라 서버가 꺼지는 중에만 거절된다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(PushCampaignSendRequestedEvent event) {
        PushCampaignSendTask task = new PushCampaignSendTask(event, sendPushUsecase, pushCampaignReverter);
        try {
            pushCampaignExecutor.execute(task);
        } catch (TaskRejectedException e) {
            task.cancel(false);
        }
    }
}
