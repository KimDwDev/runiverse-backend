package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class PasswordNotSetException extends BusinessException {

    public PasswordNotSetException() {
        super(ErrorCode.PASSWORD_NOT_SET);
    }
}
