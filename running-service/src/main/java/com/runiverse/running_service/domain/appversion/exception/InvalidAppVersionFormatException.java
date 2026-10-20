package com.runiverse.running_service.domain.appversion.exception;

import com.runiverse.running_service.domain.common.exception.AppVersionErrorCode;
import com.runiverse.running_service.domain.common.exception.BusinessException;

public class InvalidAppVersionFormatException extends BusinessException {

    public InvalidAppVersionFormatException() {
        super(AppVersionErrorCode.INVALID_APP_VERSION_FORMAT);
    }
}
