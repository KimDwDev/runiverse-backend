package com.runiverse.running_service.application.appversion.query.version;

public record CheckAppVersionQuery(
        String platform,
        String appVersion
) {

}
