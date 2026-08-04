package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.CheckEmailDuplicatePort;
import com.runiverse.running_service.application.auth.port.out.LoadUserByEmailPort;
import com.runiverse.running_service.application.auth.port.out.SaveUserPort;
import com.runiverse.running_service.domain.user.aggregate.User;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryUserStore implements SaveUserPort, CheckEmailDuplicatePort, LoadUserByEmailPort {
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
    // 검증 전용
    public int size() {
        return users.size();
    }
    public Optional<User> findById(UUID userId) {
        return Optional.ofNullable(users.get(userId));
    }
}
