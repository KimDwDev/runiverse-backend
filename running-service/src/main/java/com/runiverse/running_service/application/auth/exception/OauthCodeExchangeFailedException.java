package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class OauthCodeExchangeFailedException extends BusinessException {

    public OauthCodeExchangeFailedException() {
        super(ErrorCode.OAUTH_CODE_EXCHANGE_FAILED);
    }
}
