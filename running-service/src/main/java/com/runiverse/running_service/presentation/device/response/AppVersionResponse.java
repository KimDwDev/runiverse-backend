package com.runiverse.running_service.presentation.device.response;

public record AppVersionResponse(
        String minimumVersion,
        String latestVersion
) {

}
