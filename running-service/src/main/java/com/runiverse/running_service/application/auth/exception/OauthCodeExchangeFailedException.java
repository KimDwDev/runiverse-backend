package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.AuthErrorCode;

public class OauthCodeExchangeFailedException extends BusinessException {

    public OauthCodeExchangeFailedException() {
        super(AuthErrorCode.OAUTH_CODE_EXCHANGE_FAILED);
    }
}
