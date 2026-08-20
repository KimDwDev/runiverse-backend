package com.runiverse.running_service.domain.running.vo;

import com.runiverse.running_service.domain.running.exception.InvalidSplitIdException;

public record RunningSplitId(Long value) {

    public RunningSplitId {
        if (value == null || value < 1) {
            throw new InvalidSplitIdException();
        }
    }
}
