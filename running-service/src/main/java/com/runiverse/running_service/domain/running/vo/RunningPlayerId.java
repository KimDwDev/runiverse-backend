package com.runiverse.running_service.domain.running.vo;

import com.runiverse.running_service.domain.running.exception.InvalidRunningPlayerIdException;

public record RunningPlayerId(Long value) {

    public RunningPlayerId {
        if (value == null || value < 1) {
            throw new InvalidRunningPlayerIdException();
        }
    }
}
