package com.runiverse.running_service.domain.running.vo;

import com.runiverse.running_service.domain.running.exception.DistanceOutOfRangeException;

public record Distance(int meters) {

    private static final int MIN = 1;
    private static final int MAX = 500_000; // 500km까지 제한인데 나중에 풀수도 있다.

    public Distance {
        if (meters < MIN || meters > MAX) {
            throw new DistanceOutOfRangeException();
        }
    }
}
