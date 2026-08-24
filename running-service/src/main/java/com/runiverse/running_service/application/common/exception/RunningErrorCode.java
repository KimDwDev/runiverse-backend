package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningErrorCode implements ErrorCode {
    RUNNING_ALREADY_IN_PROGRESS("RUNNING_ALREADY_IN_PROGRESS", "이미 진행 중인 매칭이 있습니다."),
    // 아래 셋은 WS ERROR 메시지로만 나간다 — 코드·문구를 api-spec.md 5-C와 맞춘다
    ROOM_NOT_FOUND("ROOM_NOT_FOUND", "러닝 정보를 찾을 수 없습니다."),
    NOT_ROOM_PLAYER("NOT_ROOM_PLAYER", "이 방의 참가자가 아닙니다."),
    INVALID_ROOM_STATE("INVALID_ROOM_STATE", "지금은 시작할 수 없는 방입니다.");
    private final String code;
    private final String message;
}
