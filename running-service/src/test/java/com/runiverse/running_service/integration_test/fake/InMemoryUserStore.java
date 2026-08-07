package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByEmailPort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByProviderPort;
import com.runiverse.running_service.application.auth.port.out.SaveUserPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.user.vo.UserId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryUserStore implements SaveUserPort, CheckEmailDuplicatePort, LoadUserByEmailPort,
        LoadUserByProviderPort, LoadUserByIdPort {
    private final Map<UUID, User> users = new LinkedHashMap<>();
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
    // 검증 전용
    public int size() {
        return users.size();
    }
    public Optional<User> findById(UUID userId) {
        return Optional.ofNullable(users.get(userId));
    }
}
