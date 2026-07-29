package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class OauthAlreadyLinkedException extends BusinessException {
    public OauthAlreadyLinkedException() { super(ErrorCode.OAUTH_ALREADY_LINKED); }
}
