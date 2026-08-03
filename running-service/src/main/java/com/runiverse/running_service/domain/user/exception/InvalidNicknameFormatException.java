package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class InvalidNicknameFormatException extends BusinessException {
    public InvalidNicknameFormatException() { super(ErrorCode.INVALID_NICKNAME_FORMAT); }
}
