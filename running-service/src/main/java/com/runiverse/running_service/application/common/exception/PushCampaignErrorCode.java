package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 푸시 캠페인 — 없는 캠페인은 ResourceErrorCode.NOT_FOUND를 쓴다
@Getter
@RequiredArgsConstructor
public enum PushCampaignErrorCode implements ErrorCode {
    // 이미 발송을 시작한 캠페인이다 — 다시 보내려면 운영이 SQL로 DRAFT로 되돌린다
    PUSH_CAMPAIGN_ALREADY_SENT("PUSH_CAMPAIGN_ALREADY_SENT", "이미 보낸 푸시 캠페인입니다.");
    private final String code;
    private final String message;
}
