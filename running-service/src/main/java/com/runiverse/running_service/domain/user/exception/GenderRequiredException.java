package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class GenderRequiredException extends BusinessException {
    public GenderRequiredException() { super(ErrorCode.GENDER_REQUIRED); }
}
