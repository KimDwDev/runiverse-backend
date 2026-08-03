package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ErrorCode;

public class OauthNotLinkedException extends BusinessException {
    public OauthNotLinkedException() { super(ErrorCode.OAUTH_NOT_LINKED); }
}
