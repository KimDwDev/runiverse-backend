package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.AuthErrorCode;

public class OauthEmailNotProvidedException extends BusinessException {

    public OauthEmailNotProvidedException() {
        super(AuthErrorCode.OAUTH_EMAIL_NOT_PROVIDED);
    }
}
