package com.runiverse.running_service.domain.running.vo;

import com.runiverse.running_service.domain.running.exception.PaceOutOfRangeException;

public record Pace(int secondsPerKm) {

    private static final int MIN = 120; // 2분/km — 사람이 km 평균으로 낼 수 없는 값 방어
    private static final int MAX = 3600; // 60분/km — 걷기·신호 대기 구간까지 담는다
    private static final int MATCH_TOLERANCE = 30; // 초/km — 합류 허용 페이스 차

    public Pace {
        if (secondsPerKm < MIN || secondsPerKm > MAX) {
            throw new PaceOutOfRangeException();
        }
    }

    // 매칭 후보 판정 — 방 평균과 신청자 페이스가 이만큼 안에 있으면 같이 뛸 만하다
    public boolean isCloseTo(Pace other) {
        return Math.abs(secondsPerKm - other.secondsPerKm) <= MATCH_TOLERANCE;
    }

    public int gapTo(Pace other) {   // 후보가 여럿일 때 가장 가까운 방 고르기
        return Math.abs(secondsPerKm - other.secondsPerKm);
    }
}
