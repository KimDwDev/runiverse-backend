package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MatchErrorCode implements ErrorCode {
    // 활성 신청이 이미 있다 — 대기·확정뿐 아니라 러닝 중도 여기 걸린다
    MATCH_ALREADY_IN_PROGRESS("MATCH_ALREADY_IN_PROGRESS", "이미 진행 중인 매칭이 있습니다.");
    private final String code;
    private final String message;
}
