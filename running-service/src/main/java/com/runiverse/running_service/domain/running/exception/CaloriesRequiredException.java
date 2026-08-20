package com.runiverse.running_service.domain.running.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class CaloriesRequiredException extends BusinessException {

    public CaloriesRequiredException() {
        super(RunningMetricErrorCode.CALORIES_REQUIRED);
    }
}
