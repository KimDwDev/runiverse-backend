package com.runiverse.running_service.domain.device.exception;

import com.runiverse.running_service.domain.common.exception.AppVersionErrorCode;
import com.runiverse.running_service.domain.common.exception.BusinessException;

public class UnsupportedPlatformException extends BusinessException {

    public UnsupportedPlatformException() {
        super(AppVersionErrorCode.UNSUPPORTED_PLATFORM);
    }
}
