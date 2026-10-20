package com.runiverse.running_service.application.appversion.port.in;

import com.runiverse.running_service.application.appversion.query.version.GetAppVersionQuery;
import com.runiverse.running_service.application.appversion.query.version.GetAppVersionResult;

public interface GetAppVersionUsecase {

    GetAppVersionResult handle(GetAppVersionQuery query);
}
