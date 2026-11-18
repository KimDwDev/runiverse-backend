package com.runiverse.running_service.application.notification.port.out;

public interface CompletePushCampaignPort {

    // 보내기 전(DRAFT)일 때만 COMPLETED로 바꾼다 — 바꿨으면 true. 두 번 발송을 막는 유일한 장치다
    boolean complete(long pushCampaignId);
}
