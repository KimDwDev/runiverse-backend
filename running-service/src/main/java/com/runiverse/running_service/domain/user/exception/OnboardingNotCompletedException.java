package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class OnboardingNotCompletedException extends BusinessException {

    public OnboardingNotCompletedException() {
        super(ErrorCode.ONBOARDING_NOT_COMPLETED);
    }
}
