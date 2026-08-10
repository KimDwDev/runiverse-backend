package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class ProviderRequiredException extends BusinessException {

    public ProviderRequiredException() {
        super(ErrorCode.PROVIDER_REQUIRED);
    }
}
