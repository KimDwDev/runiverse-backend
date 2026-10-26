package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InviteErrorCode implements ErrorCode {
    // 지금 시각에 따라 갈리는 판정이라 요청 검증이 아니다 — 매칭의 MATCH_SLOT_CLOSED와 같은 자리다
    INVITE_START_AT_OUT_OF_RANGE("INVITE_START_AT_OUT_OF_RANGE", "선택할 수 없는 시작 시각입니다."),
    // 범위가 운영값이라 Request DTO가 못 보고 유스케이스가 본다 —
    // 클라에게는 요청 검증 실패와 같은 코드로 보인다. 실제 문구는 예외가 운영값으로 만든다
    INVALID_INVITE_OPTION("INVALID_REQUEST", "만들 수 없는 시작 시각 또는 목표 거리입니다.");
    private final String code;
    private final String message;
}
