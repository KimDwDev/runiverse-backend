package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class InvalidUserIdFormatException extends BusinessException {

    public InvalidUserIdFormatException() {
        super(UserErrorCode.INVALID_USER_ID_FORMAT);
    }
}
