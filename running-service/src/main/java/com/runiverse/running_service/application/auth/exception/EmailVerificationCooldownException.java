package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.AuthErrorCode;

public class EmailVerificationCooldownException extends BusinessException {

    public EmailVerificationCooldownException() {
        super(AuthErrorCode.EMAIL_VERIFICATION_COOLDOWN);
    }
}
