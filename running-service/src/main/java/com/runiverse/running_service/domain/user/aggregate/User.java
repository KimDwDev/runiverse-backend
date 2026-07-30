package com.runiverse.running_service.domain.user.aggregate;

import com.runiverse.running_service.domain.user.exception.LastSignInMethodException;
import com.runiverse.running_service.domain.user.exception.OauthAlreadyLinkedException;
import com.runiverse.running_service.domain.user.exception.OauthNotLinkedException;
import com.runiverse.running_service.domain.user.vo.*;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
public class User {
    private final UserId userId;
    private final Email email;
    private final PasswordHash passwordHash;
    private final boolean alertConsent;
    private final Description description;

    // 내부 저장
    private final Set<OauthUser> oauthUsers = new LinkedHashSet<>();

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

    // alertConsent, description이 없는 경우
    public User(UUID userId, String email, String passwordHash) {
        this(userId, email, passwordHash, false, "");
    }

    // oauth로 회원가입 할때 사용하는 생성자
    public User(UUID userId, String email) {
        this(userId, email, "", false, "");
    }

    // 소셜 회원가입: 유저 생성과 연결을 진행
    public static User registerWithOauth(UUID userId, String email, Provider provider, String providerId) {
        User user = new User(userId, email); // oauth용 유저 생성
        user.linkOauth(provider, providerId);
        return user;
    }

    // oauth 연결
    public void linkOauth(Provider provider, String providerId) {
        if (hasProvider(provider)) throw new OauthAlreadyLinkedException();   // 하나의
        oauthUsers.add(new OauthUser(userId, provider, providerId));
    }

    // oauth와 연결 끊기
    public void unlinkOauth(Provider provider) {
        OauthUser found = oauthUsers.stream()
                .filter(o -> o.isSameProvider(provider))
                .findFirst()
                .orElseThrow(OauthNotLinkedException::new);
        if (isLastSignInMethod()) throw new LastSignInMethodException();    // I3
        oauthUsers.remove(found);
    }

    public boolean hasProvider(Provider provider) {
        return oauthUsers.stream().anyMatch(o -> o.isSameProvider(provider));
    }
    public Set<OauthUser> getOauthUsers() {
        return Collections.unmodifiableSet(oauthUsers);
    }
    private boolean isLastSignInMethod() {
        return passwordHash.value().isEmpty() && oauthUsers.size() == 1;
    }
}
