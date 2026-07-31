package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class OnboardingAlreadyCompletedException extends BusinessException {
    public OnboardingAlreadyCompletedException() {super(ErrorCode.ONBOARDING_ALREADY_COMPLETED);}
}
