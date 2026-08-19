package com.runiverse.running_service.domain.running.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class TemperatureOutOfRangeException extends BusinessException {

    public TemperatureOutOfRangeException() {
        super(RunningMetricErrorCode.TEMPERATURE_OUT_OF_RANGE);
    }
}
