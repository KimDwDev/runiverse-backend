package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class NicknameRequiredException extends BusinessException {
    public NicknameRequiredException() { super(ErrorCode.NICKNAME_REQUIRED); }
}
