package com.runiverse.running_service.application.device.query.version;

public record GetAppVersionResult(
        String minimumVersion,
        String latestVersion
) {

}
