package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class TooManyVerificationAttemptsException extends BusinessException {
    public TooManyVerificationAttemptsException() {super(ErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS);}
}
