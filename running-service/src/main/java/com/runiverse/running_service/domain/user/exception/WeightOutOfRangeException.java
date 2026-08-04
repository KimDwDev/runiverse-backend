package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class WeightOutOfRangeException extends BusinessException {
    public WeightOutOfRangeException() { super(ErrorCode.WEIGHT_OUT_OF_RANGE); }
}
