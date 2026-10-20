package com.runiverse.running_service.application.device.port.in;

import com.runiverse.running_service.application.device.query.version.CheckAppVersionQuery;
import com.runiverse.running_service.application.device.query.version.CheckAppVersionResult;

public interface CheckAppVersionUsecase {

    CheckAppVersionResult handle(CheckAppVersionQuery query);
}
