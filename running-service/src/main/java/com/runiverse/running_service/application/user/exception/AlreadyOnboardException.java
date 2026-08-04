package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class AlreadyOnboardException extends BusinessException {
    public AlreadyOnboardException() {super(ErrorCode.ALREADY_ONBOARD);}
}
