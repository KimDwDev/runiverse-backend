package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class EmailAlreadyExistsException extends BusinessException {
    public EmailAlreadyExistsException() {
        super(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
