package com.runiverse.running_service.domain.user.vo;

import java.util.Objects;

public record Description(String value) {

    private static final int MAX_LENGTH = 100;

    public Description {
        Objects.requireNonNull(value, "소개는 null일 수 없습니다.");

        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "소개는 " + MAX_LENGTH + "자를 초과할 수 없습니다."
            );
        }
    }
}
