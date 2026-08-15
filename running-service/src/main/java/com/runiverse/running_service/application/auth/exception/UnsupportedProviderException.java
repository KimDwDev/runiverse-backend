package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.AuthErrorCode;

public class UnsupportedProviderException extends BusinessException {

    public UnsupportedProviderException() {
        super(AuthErrorCode.UNSUPPORTED_PROVIDER);
    }
}
