package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class EmailRequiredException extends BusinessException {

    public EmailRequiredException() {
        super(ErrorCode.EMAIL_REQUIRED);
    }
}
