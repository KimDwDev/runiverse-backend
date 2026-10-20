package com.runiverse.running_service.application.appversion.port.in;

import com.runiverse.running_service.application.appversion.query.version.CheckAppVersionQuery;
import com.runiverse.running_service.application.appversion.query.version.CheckAppVersionResult;

public interface CheckAppVersionUsecase {

    CheckAppVersionResult handle(CheckAppVersionQuery query);
}
