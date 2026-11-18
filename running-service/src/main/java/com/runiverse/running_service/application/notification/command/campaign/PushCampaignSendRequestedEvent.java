package com.runiverse.running_service.application.notification.command.campaign;

import com.runiverse.running_service.application.device.port.out.PushMessage;

import java.util.List;
import java.util.UUID;

// 커밋된 뒤에 보내라는 요청 — 커밋 전에 보내면 롤백돼 DRAFT로 남은 캠페인이 이미 나가 있다
public record PushCampaignSendRequestedEvent(long pushCampaignId, PushMessage message, List<UUID> recipientIds) {

    // 이벤트는 커밋 뒤 다른 스레드가 읽는다 — 발행한 뒤 명단이 바뀌지 않게 복사한다
    public PushCampaignSendRequestedEvent {
        recipientIds = List.copyOf(recipientIds);
    }
}
