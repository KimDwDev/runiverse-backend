package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class InvalidCredentials extends BusinessException {
    public InvalidCredentials() {super(ErrorCode.INVALID_CREDENTIALS);}
}
