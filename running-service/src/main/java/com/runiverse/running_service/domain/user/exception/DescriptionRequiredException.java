package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class DescriptionRequiredException extends BusinessException {

    public DescriptionRequiredException() {
        super(ErrorCode.DESCRIPTION_REQUIRED);
    }
}
