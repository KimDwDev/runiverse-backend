package com.runiverse.running_service.domain.campaign.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.PushCampaignErrorCode;

public class PushCampaignBodyRequiredException extends BusinessException {

    public PushCampaignBodyRequiredException() {
        super(PushCampaignErrorCode.PUSH_CAMPAIGN_BODY_REQUIRED);
    }
}
