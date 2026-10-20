package com.runiverse.running_service.domain.device.exception;

import com.runiverse.running_service.domain.common.exception.AppVersionErrorCode;
import com.runiverse.running_service.domain.common.exception.BusinessException;

public class InvalidVersionNameFormatException extends BusinessException {

    public InvalidVersionNameFormatException() {
        super(AppVersionErrorCode.INVALID_VERSION_NAME_FORMAT);
    }
}
