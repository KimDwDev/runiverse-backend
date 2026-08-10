package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class ProviderIdRequiredException extends BusinessException {

    public ProviderIdRequiredException() {
        super(ErrorCode.PROVIDER_ID_REQUIRED);
    }
}
