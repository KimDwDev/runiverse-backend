package com.runiverse.running_service.domain.running.vo;

import com.runiverse.running_service.domain.running.exception.GpsTrackKeyRequiredException;
import com.runiverse.running_service.domain.running.exception.GpsTrackKeyTooLongException;

public record GpsTrackKey(String value) {

    private static final int MAX_LENGTH = 255;

    public GpsTrackKey {
        if (value == null) {
            throw new GpsTrackKeyRequiredException();
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new GpsTrackKeyRequiredException();
        }
        if (value.length() > MAX_LENGTH) {
            throw new GpsTrackKeyTooLongException();
        }
    }
}
