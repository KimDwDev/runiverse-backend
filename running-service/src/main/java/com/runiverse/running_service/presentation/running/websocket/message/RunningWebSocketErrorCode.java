package com.runiverse.running_service.presentation.running.websocket.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningWebSocketErrorCode {
    // 봉투 자체를 못 읽음 — REST의 MALFORMED_REQUEST_BODY에 대응
    MALFORMED_MESSAGE("MALFORMED_MESSAGE", "메시지 형식이 올바르지 않습니다."),
    MISSING_MESSAGE_TYPE("MISSING_MESSAGE_TYPE", "메시지 타입이 없습니다."),
    UNSUPPORTED_MESSAGE_TYPE("UNSUPPORTED_MESSAGE_TYPE", "지원하지 않는 메시지 타입입니다."),
    INVALID_REQUEST("INVALID_REQUEST", "요청 형식이 올바르지 않습니다.");
    private final String code;
    private final String message;
}
