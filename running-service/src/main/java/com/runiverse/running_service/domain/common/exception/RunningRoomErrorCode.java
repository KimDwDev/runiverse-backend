package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningRoomErrorCode implements ErrorCode {
    ROOM_TYPE_REQUIRED("ROOM_TYPE_REQUIRED", "러닝방 종류는 필수입니다."),
    UNSUPPORTED_ROOM_TYPE("UNSUPPORTED_ROOM_TYPE", "지원하지 않는 러닝방 종류입니다."),
    INVALID_ROOM_STATUS_TRANSITION("INVALID_ROOM_STATUS_TRANSITION", "허용되지 않는 러닝방 상태 변경입니다.");
    private final String code;
    private final String message;
}
