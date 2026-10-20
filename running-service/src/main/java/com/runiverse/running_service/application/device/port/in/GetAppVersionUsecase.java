package com.runiverse.running_service.application.device.port.in;

import com.runiverse.running_service.application.device.query.version.GetAppVersionQuery;
import com.runiverse.running_service.application.device.query.version.GetAppVersionResult;

public interface GetAppVersionUsecase {

    GetAppVersionResult handle(GetAppVersionQuery query);
}
