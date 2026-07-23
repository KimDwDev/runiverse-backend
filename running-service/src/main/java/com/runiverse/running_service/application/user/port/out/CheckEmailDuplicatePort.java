package com.runiverse.running_service.application.user.port.out;

public interface CheckEmailDuplicatePort {
    boolean existsByEmail(String email);
}
