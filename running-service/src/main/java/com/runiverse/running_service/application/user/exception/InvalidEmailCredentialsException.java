package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class InvalidEmailCredentialsException extends BusinessException {
    public InvalidEmailCredentialsException() {
        super(ErrorCode.INVALID_EMAIL_CREDENTIALS);
    }
}
