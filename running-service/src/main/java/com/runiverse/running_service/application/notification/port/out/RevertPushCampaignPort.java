package com.runiverse.running_service.application.notification.port.out;

public interface RevertPushCampaignPort {

    // 보낸 상태(COMPLETED)일 때만 DRAFT로 되돌린다 — 되돌렸으면 true. 발송을 시작하지 못한 캠페인에만 부른다
    boolean revert(long pushCampaignId);
}
