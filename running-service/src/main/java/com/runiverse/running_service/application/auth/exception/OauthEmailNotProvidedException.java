package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class OauthEmailNotProvidedException extends BusinessException {
    public OauthEmailNotProvidedException() { super(ErrorCode.OAUTH_EMAIL_NOT_PROVIDED); }
}
