package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.AvgPaceOutOfRangeException;

public record AvgPace(int secondPerKm) {
    private static final int MIN = 120; // 최소 페이스
    private static final int MAX = 1800; // 최대 페이스
    public AvgPace {
        if (secondPerKm < MIN || secondPerKm > MAX) throw new AvgPaceOutOfRangeException();
    }
}
