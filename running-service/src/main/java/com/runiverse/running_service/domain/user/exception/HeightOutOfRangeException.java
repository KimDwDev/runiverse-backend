package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class HeightOutOfRangeException extends BusinessException {

    public HeightOutOfRangeException() {
        super(ErrorCode.HEIGHT_OUT_OF_RANGE);
    }
}
