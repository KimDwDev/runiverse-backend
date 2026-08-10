package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.aggregate.UserOnboard;

public interface SaveOnboardPort {

    void saveOnboard(UserOnboard onboard);
}
