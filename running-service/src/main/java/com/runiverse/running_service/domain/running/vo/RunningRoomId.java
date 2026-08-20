package com.runiverse.running_service.domain.running.vo;

import com.runiverse.running_service.domain.running.exception.InvalidRunningRoomIdException;

public record RunningRoomId(Long value) {

    public RunningRoomId {
        if (value == null || value < 1) {
            throw new InvalidRunningRoomIdException();
        }
    }
}
