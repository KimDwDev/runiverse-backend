package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByEmailPort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByProviderPort;
import com.runiverse.running_service.application.auth.port.out.SaveUserPort;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.UpdatePasswordPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.vo.PasswordHash;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.user.vo.UserId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryUserStore implements SaveUserPort, CheckEmailDuplicatePort, LoadUserByEmailPort,
        LoadUserByProviderPort, LoadUserByIdPort, UpdatePasswordPort {

    private final Map<UUID, User> users = new LinkedHashMap<>();
    private final Map<UUID, PasswordHash> updatedPasswords = new LinkedHashMap<>();

    @Override
    public User save(User user) {
        users.put(user.getUserId().value(), user);
        return user;
    }

    @Override
    public boolean existsByEmail(String email) {
        return loadByEmail(email).isPresent();
    }

    @Override
    public Optional<User> loadByEmail(String email) {
        return users.values().stream()
                .filter(user -> user.getEmail().value().equals(email))
                .findFirst();
    }

    @Override
    public Optional<User> loadByProvider(Provider provider, String providerId) {
        return users.values().stream()
                .filter(user -> user.getOauthUser()
                        .filter(oauth -> oauth.getProvider() == provider
                                && oauth.getProviderId().value().equals(providerId))
                        .isPresent())
                .findFirst();
    }

    @Override
    public Optional<User> loadById(UserId userId) {
        return Optional.ofNullable(users.get(userId.value()));
    }

    @Override
    public void updatePassword(UserId userId, PasswordHash passwordHash) {
        User user = users.get(userId.value());
        if (user == null) {
            throw new UserNotFoundException();
        }
        user.changePassword(passwordHash.value());
        updatedPasswords.put(userId.value(), passwordHash);
    }

    // 검증 전용
    public int size() {
        return users.size();
    }

    public Optional<User> findById(UUID userId) {
        return Optional.ofNullable(users.get(userId));
    }

    // 애그리거트를 참조로 들고 있어 핸들러가 changePassword만 해도 위 findById의 값은 바뀐다.
    // 포트를 실제로 불렀는지는 이걸로 확인한다
    public Optional<PasswordHash> findUpdatedPassword(UUID userId) {
        return Optional.ofNullable(updatedPasswords.get(userId));
    }
}
