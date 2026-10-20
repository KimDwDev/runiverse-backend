package com.runiverse.running_service.application.device.query.version;

public record CheckAppVersionQuery(
        String platform,
        String appVersion
) {

}
