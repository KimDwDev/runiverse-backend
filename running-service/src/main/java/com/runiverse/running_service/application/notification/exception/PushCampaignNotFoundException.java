package com.runiverse.running_service.application.notification.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

public class PushCampaignNotFoundException extends BusinessException {

    public PushCampaignNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
