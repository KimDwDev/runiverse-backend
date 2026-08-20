package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.query.profile.GetProfileQuery;
import com.runiverse.running_service.application.user.query.profile.GetProfileResult;

public interface GetProfileUsecase {

    GetProfileResult handle(GetProfileQuery query);
}
