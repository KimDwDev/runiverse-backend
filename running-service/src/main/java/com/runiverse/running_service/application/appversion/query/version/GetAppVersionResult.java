package com.runiverse.running_service.application.appversion.query.version;

public record GetAppVersionResult(
        String minimumVersion,
        String latestVersion
) {

}
