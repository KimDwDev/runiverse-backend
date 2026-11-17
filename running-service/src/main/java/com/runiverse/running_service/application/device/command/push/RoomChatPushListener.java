package com.runiverse.running_service.application.device.command.push;

import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RoomChatPushListener {

    private final SendPushUsecase sendPushUsecase;

    @Async("chatPushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(RoomChatPushRequestedEvent event) {
        sendPushUsecase.handle(new SendPushCommand(event.userId(), event.message()));
    }
}
