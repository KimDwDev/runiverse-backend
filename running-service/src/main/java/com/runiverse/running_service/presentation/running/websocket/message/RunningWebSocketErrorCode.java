package com.runiverse.running_service.presentation.running.websocket.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningWebSocketErrorCode {
    INVALID_REQUEST("INVALID_REQUEST", "요청 형식이 올바르지 않습니다.");
    private final String code;
    private final String message;
}
