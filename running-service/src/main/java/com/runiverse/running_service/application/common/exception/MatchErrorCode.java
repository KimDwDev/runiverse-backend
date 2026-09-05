package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MatchErrorCode implements ErrorCode {
    // 활성 신청이 이미 있다 — 대기·확정뿐 아니라 러닝 중도 여기 걸린다
    MATCH_ALREADY_IN_PROGRESS("MATCH_ALREADY_IN_PROGRESS", "이미 진행 중인 매칭이 있습니다."),
    // 모집 마감(start_at - 오프셋)이 지난 슬롯 — 프론트는 /slots의 selectable로 1차 차단한다.
    // 모달을 열어둔 사이 마감이 지나가는 경합에서만 걸린다
    MATCH_SLOT_CLOSED("MATCH_SLOT_CLOSED", "이미 모집이 마감된 시간대입니다.");
    private final String code;
    private final String message;
}
