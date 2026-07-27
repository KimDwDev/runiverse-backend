package com.runiverse.running_service.domain.user.vo;

import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final int MAX_LENGTH = 254;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public Email {
        Objects.requireNonNull(value, "이메일은 필수입니다.");

        value = value.trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException("이메일은 비어 있을 수 없습니다.");
        }

        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("이메일은 254자를 초과할 수 없습니다.");
        }

        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
        }
    }

}