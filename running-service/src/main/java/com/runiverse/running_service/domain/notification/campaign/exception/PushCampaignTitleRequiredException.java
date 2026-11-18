package com.runiverse.running_service.domain.notification.campaign.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.PushCampaignErrorCode;

public class PushCampaignTitleRequiredException extends BusinessException {

    public PushCampaignTitleRequiredException() {
        super(PushCampaignErrorCode.PUSH_CAMPAIGN_TITLE_REQUIRED);
    }
}
