package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class UnsupportedProviderException extends BusinessException {
    public UnsupportedProviderException() { super(ErrorCode.UNSUPPORTED_PROVIDER); }
}
