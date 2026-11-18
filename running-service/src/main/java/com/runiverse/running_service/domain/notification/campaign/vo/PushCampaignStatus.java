package com.runiverse.running_service.domain.notification.campaign.vo;

public enum PushCampaignStatus {
    DRAFT,
    // 발송을 시작해 다시 보내지 않는다 — 보내다 멈춰도 이 값이다
    COMPLETED
}
