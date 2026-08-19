package com.runiverse.running_service.domain.running.vo;

import com.runiverse.running_service.domain.running.exception.PaceOutOfRangeException;

public record Pace(int secondsPerKm) {

    private static final int MIN = 120; // 2분/km — 사람이 km 평균으로 낼 수 없는 값 방어
    private static final int MAX = 3600; // 60분/km — 걷기·신호 대기 구간까지 담는다

    public Pace {
        if (secondsPerKm < MIN || secondsPerKm > MAX) {
            throw new PaceOutOfRangeException();
        }
    }
}
