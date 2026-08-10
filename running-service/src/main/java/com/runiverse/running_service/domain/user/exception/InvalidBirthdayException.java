package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class InvalidBirthdayException extends BusinessException {

    public InvalidBirthdayException() {
        super(ErrorCode.INVALID_BIRTHDAY);
    }
}
