package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class InvalidPasswordCredentialsException extends BusinessException {
    public InvalidPasswordCredentialsException() {
        super(ErrorCode.INVALID_PASSWORD_CREDENTIALS);
    }
}
