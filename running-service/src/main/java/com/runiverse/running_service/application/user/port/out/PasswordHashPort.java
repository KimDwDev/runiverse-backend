package com.runiverse.running_service.application.user.port.out;

public interface PasswordHashPort {
    String hash(String rawPassword);
}
