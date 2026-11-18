package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PushCampaignErrorCode implements ErrorCode {
    PUSH_CAMPAIGN_TITLE_REQUIRED("PUSH_CAMPAIGN_TITLE_REQUIRED", "푸시 캠페인 제목은 필수입니다."),
    PUSH_CAMPAIGN_BODY_REQUIRED("PUSH_CAMPAIGN_BODY_REQUIRED", "푸시 캠페인 본문은 필수입니다.");
    private final String code;
    private final String message;
}
