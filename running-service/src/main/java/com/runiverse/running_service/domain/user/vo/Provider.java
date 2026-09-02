package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.ProviderNotSupportedException;
import com.runiverse.running_service.domain.user.exception.ProviderRequiredException;

import java.util.Locale;

public enum Provider {
    KAKAO,
    GOOGLE;

    public static Provider from(String value) {
        if (value == null) {
            throw new ProviderRequiredException();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new ProviderRequiredException();
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new ProviderNotSupportedException();
        }
    }
}
