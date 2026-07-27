package com.runiverse.running_service.domain.user.vo;

import java.util.Objects;
import java.util.regex.Pattern;

// 나중에
public record PasswordHash(String value) {
    private static final Pattern ARGON2_PATTERN = Pattern.compile(
            "^\\$argon2id\\$v=\\d+\\$m=\\d+,t=\\d+,p=\\d+\\$[A-Za-z0-9+/]+={0,2}\\$[A-Za-z0-9+/]+={0,2}$"
    );

    public PasswordHash {
        Objects.requireNonNull(value, "비밀번호 해시는 필수입니다.");

        if (!value.isEmpty() && !ARGON2_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("비밀번호 해시는 빈 값이거나 올바른 Argon2id 형식이어야 합니다");
        }
    }
}
