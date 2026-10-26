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
    INVALID_INVITE_OPTION("INVALID_REQUEST", "만들 수 없는 시작 시각 또는 목표 거리입니다."),
    // 모집 중이 아니거나 모집 마감(start_at - 초대 마감 오프셋)이 지났다 — 초대·수락이 함께 쓴다
    RUNNING_INVITATION_CLOSED("RUNNING_INVITATION_CLOSED", "초대를 받을 수 없는 방입니다."),
    // 화면에서 고른 목록이 친구 목록이라, 섞였다면 그사이 관계가 바뀐 것이다 — 아무에게도 보내지 않는다
    NOT_FRIEND("NOT_FRIEND", "친구에게만 초대를 보낼 수 있습니다."),
    // 이미 수락·거절했거나 나갔다 — 다시 들어가려면 다시 초대받아야 한다
    RUNNING_INVITATION_NOT_PENDING("RUNNING_INVITATION_NOT_PENDING", "이미 응답한 초대입니다."),
    // 자리가 다 찼다 — 초대는 PENDING으로 남아 누가 나가면 다시 수락할 수 있다
    RUNNING_ROOM_FULL("RUNNING_ROOM_FULL", "방이 가득 찼습니다.");
    private final String code;
    private final String message;
}
