package com.runiverse.running_service.presentation.match.response;

// 앱 스트림 HOME_WAITING 이벤트의 data — 오늘 신청할 수 있는 칸의 대기 인원 합계
public record WaitingCountEventResponse(
        int waitingCount
) {

    public static WaitingCountEventResponse from(int waitingCount) {
        return new WaitingCountEventResponse(waitingCount);
    }
}
