package com.runiverse.running_service.domain.running.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class InvalidRouteRangeException extends BusinessException {

    public InvalidRouteRangeException() {
        super(RunningRecordErrorCode.INVALID_ROUTE_RANGE);
    }
}
