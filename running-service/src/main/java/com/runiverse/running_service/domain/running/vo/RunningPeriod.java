package com.runiverse.running_service.domain.running.vo;

import com.runiverse.running_service.domain.running.exception.InvalidRunningPeriodException;
import com.runiverse.running_service.domain.running.exception.RunningPeriodRequiredException;

import java.time.LocalDateTime;

public record RunningPeriod(LocalDateTime startAt, LocalDateTime endAt) {

    public RunningPeriod {
        if (startAt == null || endAt == null) {
            throw new RunningPeriodRequiredException();
        }
        if (!endAt.isAfter(startAt)) {
            throw new InvalidRunningPeriodException();
        }
    }
    
}
