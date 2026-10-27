package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.InviteErrorCode;

// 하나라도 섞이면 아무에게도 보내지 않는다 — 그사이 관계가 바뀐 것이라 다시 고르게 한다
public class NotFriendException extends BusinessException {

    public NotFriendException() {
        super(InviteErrorCode.NOT_FRIEND);
    }
}
