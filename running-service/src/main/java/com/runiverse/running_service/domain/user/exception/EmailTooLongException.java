package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class EmailTooLongException extends BusinessException {

    public EmailTooLongException() {
        super(ErrorCode.EMAIL_TOO_LONG);
    }
}
