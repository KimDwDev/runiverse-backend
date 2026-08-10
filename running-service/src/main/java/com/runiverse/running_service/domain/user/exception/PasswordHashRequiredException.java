package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class PasswordHashRequiredException extends BusinessException {

    public PasswordHashRequiredException() {
        super(ErrorCode.PASSWORD_HASH_REQUIRED);
    }
}
