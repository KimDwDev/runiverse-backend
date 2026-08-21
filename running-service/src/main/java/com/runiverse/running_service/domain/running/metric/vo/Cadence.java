package com.runiverse.running_service.domain.running.metric.vo;

import com.runiverse.running_service.domain.running.metric.exception.CadenceOutOfRangeException;

public record Cadence(int stepsPerMinute) {

    private static final int MIN = 1;
    private static final int MAX = 300; // 엘리트도 겨우 가능하다고 한다.

    public Cadence {
        if (stepsPerMinute < MIN || stepsPerMinute > MAX) {
            throw new CadenceOutOfRangeException();
        }
    }
}
