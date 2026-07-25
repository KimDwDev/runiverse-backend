package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class UserIdRequiredException extends BusinessException {
    public UserIdRequiredException() {
        super(ErrorCode.USER_ID_REQUIRED);
    }
}
