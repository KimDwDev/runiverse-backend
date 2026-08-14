package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class ProviderNotSupportedException extends BusinessException {

    public ProviderNotSupportedException() {
        super(ErrorCode.UNSUPPORTED_PROVIDER);
    }
}
