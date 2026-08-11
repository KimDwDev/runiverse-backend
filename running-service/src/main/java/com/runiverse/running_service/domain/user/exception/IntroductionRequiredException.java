package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class IntroductionRequiredException extends BusinessException {

    public IntroductionRequiredException() {
        super(ErrorCode.INTRODUCTION_REQUIRED);
    }
}
