package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class WeightRequiredException extends BusinessException {
    public WeightRequiredException() {super(ErrorCode.WEIGHT_REQUIRED);}
}
