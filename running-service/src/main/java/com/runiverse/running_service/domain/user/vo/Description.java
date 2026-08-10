package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.DescriptionRequiredException;
import com.runiverse.running_service.domain.user.exception.DescriptionTooLongException;

public record Description(String value) {

    private static final int MAX_LENGTH = 100;

    public Description {
        if (value == null) {
            throw new DescriptionRequiredException();
        }

        if (value.length() > MAX_LENGTH) {
            throw new DescriptionTooLongException();
        }
    }
}
