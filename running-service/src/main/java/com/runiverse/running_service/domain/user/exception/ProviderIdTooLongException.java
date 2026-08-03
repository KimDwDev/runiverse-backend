package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class ProviderIdTooLongException extends BusinessException {
    public ProviderIdTooLongException() { super(ErrorCode.PROVIDER_ID_TOO_LONG); }
}
