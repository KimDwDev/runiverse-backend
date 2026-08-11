package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class ProfileImageKeyRequiredException extends BusinessException {

    public ProfileImageKeyRequiredException() {
        super(ErrorCode.PROFILE_IMAGE_KEY_REQUIRED);
    }

}
