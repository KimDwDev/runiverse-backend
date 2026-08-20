package com.runiverse.running_service.domain.running.vo;

import com.runiverse.running_service.domain.running.exception.ElevationGainOutOfRangeException;

public record ElevationGain(int meters) {

    private static final int MIN = 0;
    private static final int MAX = 20_000;

    public ElevationGain {
        if (meters < MIN || meters > MAX) {
            throw new ElevationGainOutOfRangeException();
        }
    }
}
