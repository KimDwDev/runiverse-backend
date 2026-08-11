package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class AlreadyOnboardingException extends BusinessException {

    public AlreadyOnboardingException() {
        super(ErrorCode.ALREADY_ONBOARDED);
    }
}
