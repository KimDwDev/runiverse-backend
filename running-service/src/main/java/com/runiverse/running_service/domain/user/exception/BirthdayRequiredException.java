package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class BirthdayRequiredException extends BusinessException {
    public BirthdayRequiredException() { super(ErrorCode.BIRTHDAY_REQUIRED); }
}
