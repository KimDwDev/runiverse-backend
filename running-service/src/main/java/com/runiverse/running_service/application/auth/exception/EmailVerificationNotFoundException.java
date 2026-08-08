package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class EmailVerificationNotFoundException extends BusinessException {
    public EmailVerificationNotFoundException() {super(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND);}
}
