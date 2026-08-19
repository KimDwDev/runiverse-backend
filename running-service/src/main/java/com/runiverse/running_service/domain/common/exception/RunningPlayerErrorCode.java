package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningPlayerErrorCode implements ErrorCode {
    INVALID_PLAYER_STATUS_TRANSITION("INVALID_PLAYER_STATUS_TRANSITION", "허용되지 않는 참가자 상태 변경입니다."),
    PLAYER_ALREADY_LEFT("PLAYER_ALREADY_LEFT", "이미 종료된 참가 신청입니다.");
    private final String code;
    private final String message;
}
