package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class UserIdRequiredException extends BusinessException {

    public UserIdRequiredException() {
        super(UserErrorCode.USER_ID_REQUIRED);
    }
}
