package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.aggregate.User;

import java.util.Optional;

public interface LoadUserByEmailPort {
    public Optional<User> loadByEmail(String email);
}
