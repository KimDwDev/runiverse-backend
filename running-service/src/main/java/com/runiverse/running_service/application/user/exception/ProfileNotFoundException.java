package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.UserErrorCode;

public class ProfileNotFoundException extends BusinessException {

    public ProfileNotFoundException() {
        super(UserErrorCode.PROFILE_NOT_FOUND);
    }
}
