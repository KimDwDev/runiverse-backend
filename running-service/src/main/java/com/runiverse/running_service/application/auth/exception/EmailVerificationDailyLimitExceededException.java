package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class EmailVerificationDailyLimitExceededException extends BusinessException {
    public EmailVerificationDailyLimitExceededException() {super(ErrorCode.EMAIL_VERIFICATION_DAILY_LIMIT_EXCEEDED);}
}
