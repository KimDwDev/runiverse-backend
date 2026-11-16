package com.runiverse.running_service.application.campaign.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.PushCampaignErrorCode;

public class PushCampaignAlreadySentException extends BusinessException {

    public PushCampaignAlreadySentException() {
        super(PushCampaignErrorCode.PUSH_CAMPAIGN_ALREADY_SENT);
    }
}
