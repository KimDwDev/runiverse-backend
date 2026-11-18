package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomChatErrorCode;

public class RunningRoomChatTypeRequiredException extends BusinessException {

    public RunningRoomChatTypeRequiredException() {
        super(RunningRoomChatErrorCode.RUNNING_ROOM_CHAT_TYPE_REQUIRED);
    }
}
