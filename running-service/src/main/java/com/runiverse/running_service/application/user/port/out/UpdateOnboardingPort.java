package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.aggregate.UserOnboarding;

public interface UpdateOnboardingPort {

    void updateOnboarding(UserOnboarding onboarding);
}
