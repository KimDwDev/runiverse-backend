package com.runiverse.running_service.domain.running.vo;

import com.runiverse.running_service.domain.running.exception.ElapsedTimeOutOfRangeException;

public record ElapsedTime(int seconds) {

    private static final int MIN = 1;
    private static final int MAX = 86_400; // 250 시간 (500km 기준)

    public ElapsedTime {
        if (seconds < MIN || seconds > MAX) {
            throw new ElapsedTimeOutOfRangeException();
        }
    }

    public ElapsedTime plus(ElapsedTime other) {
        return new ElapsedTime(seconds + other.seconds);
    }
}
