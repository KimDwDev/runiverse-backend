package com.runiverse.running_service.domain.appversion.exception;

import com.runiverse.running_service.domain.common.exception.AppVersionErrorCode;
import com.runiverse.running_service.domain.common.exception.BusinessException;

public class PlatformRequiredException extends BusinessException {

    public PlatformRequiredException() {
        super(AppVersionErrorCode.PLATFORM_REQUIRED);
    }
}
