package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class UnsupportedProviderException extends BusinessException {

    public UnsupportedProviderException() {
        super(ErrorCode.UNSUPPORTED_PROVIDER);
    }
}
