package com.runiverse.running_service.domain.appversion.exception;

import com.runiverse.running_service.domain.common.exception.AppVersionErrorCode;
import com.runiverse.running_service.domain.common.exception.BusinessException;

public class AppVersionRequiredException extends BusinessException {

    public AppVersionRequiredException() {
        super(AppVersionErrorCode.APP_VERSION_REQUIRED);
    }
}
