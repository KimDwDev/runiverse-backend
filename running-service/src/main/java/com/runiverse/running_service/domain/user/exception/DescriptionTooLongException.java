package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class DescriptionTooLongException extends BusinessException {

    public DescriptionTooLongException() {
        super(ErrorCode.DESCRIPTION_TOO_LONG);
    }
}
