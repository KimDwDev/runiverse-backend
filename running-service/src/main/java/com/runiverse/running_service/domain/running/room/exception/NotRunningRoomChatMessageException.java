package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomChatErrorCode;

public class NotRunningRoomChatMessageException extends BusinessException {

    public NotRunningRoomChatMessageException() {
        super(RunningRoomChatErrorCode.NOT_RUNNING_ROOM_CHAT_MESSAGE);
    }
}
