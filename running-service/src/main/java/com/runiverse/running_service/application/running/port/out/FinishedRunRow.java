package com.runiverse.running_service.application.running.port.out;

// 완주 기록 한 건 — 닉네임은 원문이다. 가리는 건 소식을 만드는 쪽이 한다
public record FinishedRunRow(
        Long runningRecordId,
        String nickname,
        int distanceMeters   // 방 목표 거리, 목표가 없는 솔로면 실제 거리
) {

}
