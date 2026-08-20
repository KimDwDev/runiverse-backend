package com.runiverse.running_service.presentation.running.websocket.message;

public record ErrorPayload(String code, String message, String sourceType) {

    public static ErrorPayload of(RunningWebSocketErrorCode errorCode, String sourceType) {
        return new ErrorPayload(errorCode.getCode(), errorCode.getMessage(), sourceType);
    }
}
