package com.runiverse.running_service.domain.user.aggregate;

import com.runiverse.running_service.domain.user.vo.*;
import lombok.Getter;

import java.util.UUID;

@Getter
public class User {
    private final UserId userId;
    private final Email email;
    private final PasswordHash passwordHash;
    private final boolean alertConsent;
    private final Description description;

    // 생성자 부분 작성
    public User(UUID userId, String email, String passwordHash, boolean alertConsent, String description) {
        this.userId = new UserId(userId);
        this.email = new Email(email);
        this.passwordHash = new PasswordHash(passwordHash);
        this.alertConsent = alertConsent;
        this.description = new Description(description);
    }

    // 로컬 회원가입 할때 사용하는 생성자
    public User(UUID userId, String email, String passwordHash, boolean alertConsent) {
        this(userId, email, passwordHash, alertConsent, "");
    }

    // oauth로 회원가입 할때 사용하는 생성자
    public User(UUID userId, String email) {
        this(userId, email, "", false, "");
    }

    // alertConsent, description이 없는 경우
    public User(UUID userId, String email, String passwordHash) {
        this(userId, email, passwordHash, false, "");
    }

}
