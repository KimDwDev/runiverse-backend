package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.aggregate.User;

public interface SaveUserPort {
    User save(User user);
}
