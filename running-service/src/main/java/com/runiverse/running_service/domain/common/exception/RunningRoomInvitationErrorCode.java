package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningRoomInvitationErrorCode implements ErrorCode {
    INVALID_RUNNING_ROOM_INVITATION_ID("INVALID_RUNNING_ROOM_INVITATION_ID", "러닝 초대 ID가 올바르지 않습니다."),
    SELF_INVITATION("SELF_INVITATION", "자기 자신은 초대할 수 없습니다."),
    INVALID_INVITATION_STATUS_TRANSITION("INVALID_INVITATION_STATUS_TRANSITION", "허용되지 않는 초대 상태 변경입니다.");
    private final String code;
    private final String message;
}
