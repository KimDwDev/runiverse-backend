package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;

public class EmailSendFailedException extends BusinessException {
    public EmailSendFailedException() {super(ErrorCode.EMAIL_SEND_FAILED);}
}
