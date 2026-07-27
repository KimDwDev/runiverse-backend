package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class InvalidPasswordHashFormatException extends BusinessException {
    public InvalidPasswordHashFormatException() {
        super(ErrorCode.INVALID_PASSWORD_HASH_FORMAT);
    }
}
