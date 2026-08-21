package com.runiverse.running_service.domain.running.metric.vo;

import com.runiverse.running_service.domain.running.metric.exception.ElevationChangeOutOfRangeException;

public record ElevationChange(int meters) {

    private static final int MIN = -10_000;
    private static final int MAX = 10_000;

    public ElevationChange {
        if (meters < MIN || meters > MAX) {
            throw new ElevationChangeOutOfRangeException();
        }
    }
}
