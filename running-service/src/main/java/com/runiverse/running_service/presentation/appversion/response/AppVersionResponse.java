package com.runiverse.running_service.presentation.appversion.response;

public record AppVersionResponse(
        String minimumVersion,
        String latestVersion
) {

}
