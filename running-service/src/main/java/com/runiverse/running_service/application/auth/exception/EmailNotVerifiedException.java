package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.AuthErrorCode;

public class EmailNotVerifiedException extends BusinessException {

    public EmailNotVerifiedException() {
        super(AuthErrorCode.EMAIL_NOT_VERIFIED);
    }
}
